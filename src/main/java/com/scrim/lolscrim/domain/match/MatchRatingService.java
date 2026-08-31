package com.scrim.lolscrim.domain.match;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.scrim.lolscrim.domain.player.GlickoCalculator;
import com.scrim.lolscrim.domain.player.Lane;
import com.scrim.lolscrim.domain.player.PlayerLaneRating;
import com.scrim.lolscrim.domain.player.PlayerLaneRatingRepository;
import com.scrim.lolscrim.domain.player.PlayerRating;
import com.scrim.lolscrim.domain.player.PlayerRatingRepository;
import com.scrim.lolscrim.domain.player.RatingHistory;
import com.scrim.lolscrim.domain.player.RatingHistoryRepository;
import com.scrim.lolscrim.domain.player.RatingScope;
import com.scrim.lolscrim.domain.player.RatingSeedCalculator;
import com.scrim.lolscrim.domain.player.SeedSource;
import com.scrim.lolscrim.domain.session.TeamSide;

import lombok.RequiredArgsConstructor;

/**
 * 확정된 내전 결과를 선수 점수에 반영한다. DESIGN.md §4.2 / §4.3.
 *
 * <p>솔랭 점수는 §4.1-B 로 <b>최초 시드</b>만 잡고, 그 뒤로는 이 클래스가 내전 결과로 점수를 굴린다.
 * 시드 로직({@link RatingSeedCalculator})과 갱신 로직({@link GlickoCalculator})이 갈라져 있는 이유다.
 *
 * <p>점수는 파괴적으로 갱신되므로 움직인 이유를 {@link RatingHistory} 와
 * {@code match_participants.rating_before/after/delta} 양쪽에 남긴다 (§4.2-B).
 * 이게 없으면 {@code MatchStatus.VOIDED}(결과 무효화) 때 되돌릴 근거가 사라진다.
 */
@Service
@RequiredArgsConstructor
public class MatchRatingService {

	private final MatchParticipantRepository participantRepository;
	private final PlayerRatingRepository ratingRepository;
	private final PlayerLaneRatingRepository laneRatingRepository;
	private final RatingHistoryRepository historyRepository;

	/** 한 팀의 갱신 <b>전</b> 평균. 상대팀 기준값이라 계산 도중 변하면 안 된다. */
	private record TeamAverage(double rating, double rd) {
	}

	/**
	 * 선언이 필요하다. 지금은 {@code acceptResult} 의 ambient 트랜잭션 안에서만 불리지만,
	 * 재계산 잡·관리자 API 가 이 public 메서드를 직접 부르면 {@code saveAll(seeded)} 만
	 * 자체 트랜잭션으로 저장되고 나머지 갱신과 {@code rating_applied} 는 detached 로 조용히 유실된다.
	 * 기존 경로는 REQUIRED 전파라 영향이 없다.
	 *
	 * @param ratingEnabled 세션의 점수 반영 여부. 친선전(false)은 전적만 남고 점수는 안 움직인다.
	 */
	@Transactional
	public void apply(ScrimMatch match, boolean ratingEnabled, LocalDateTime now) {
		if (match.isRatingApplied() || !ratingEnabled || match.getWinnerSide() == null) {
			return;
		}
		List<MatchParticipant> participants = participantRepository.findAllByMatchId(match.getId());
		if (participants.isEmpty()) {
			return;
		}

		Map<Long, PlayerRating> ratings = loadOrSeedRatings(participants, match.getRoomId());
		// 팀 평균은 반드시 아무도 갱신하기 전에 확정한다.
		// 루프 안에서 그때그때 계산하면 먼저 처리된 선수의 새 점수가 뒤 선수의 기준값을 오염시킨다.
		Map<TeamSide, TeamAverage> averages = teamAverages(participants, ratings);
		Map<Long, Map<Lane, PlayerLaneRating>> laneRatings = loadLaneRatings(participants);
		List<RatingHistory> histories = new ArrayList<>();

		for (MatchParticipant participant : participants) {
			PlayerRating rating = ratings.get(participant.getPlayerId());
			TeamAverage opponent = averages.get(opposite(participant.getSide()));
			if (rating == null || opponent == null) {
				continue;
			}
			boolean win = participant.getSide() == match.getWinnerSide();
			if (rating.isLocked()) {
				// 잠긴 선수는 점수를 건드리지 않되, 왜 안 움직였는지는 참가 기록에 남긴다
				participant.recordRatingChange(rating.getRating(), rating.getRating());
				continue;
			}
			double offRoleFactor = participant.getOffRoleFactor().doubleValue();

			GlickoCalculator.Outcome outcome = GlickoCalculator.update(
					rating.getRating(), rating.getRd(), opponent.rating(), opponent.rd(), win);
			int before = rating.getRating();
			int rdBefore = rating.getRd();
			int after = damp(before, outcome.rawDelta(), offRoleFactor);

			rating.applyMatchResult(after, outcome.newRd(), win, now);
			participant.recordRatingChange(before, after);
			histories.add(RatingHistory.ofMatch(
					participant.getPlayerId(),
					match.getRoomId(),
					match.getId(),
					RatingScope.OVERALL,
					null,
					before,
					after,
					rdBefore,
					outcome.newRd(),
					outcome.expectedScore(),
					participant.getOffRoleFactor(),
					participant.getAssignedFrom().name()));

			applyLaneResult(laneRatings, participant, match, opponent, win, offRoleFactor, histories);
		}
		// 이력은 한 번에 저장한다 — uk_rh_match_player_scope 가 이중 반영의 DB 레벨 백스톱이다
		historyRepository.saveAll(histories);
		match.markRatingApplied(now);
	}

	/**
	 * 실제로 뛴 라인만 갱신한다 (§4.2). 상대 기준값은 전체와 같은 "상대팀 평균"을 쓰고,
	 * 내 rating/RD 만 라인 전용 값으로 바꿔 넣는다.
	 *
	 * <p>라인 행이 없으면 건너뛴다. 없는 걸 즉석에서 만들면 {@code self_proficiency} 를
	 * 0(=배정 금지)으로 넣게 되어, 점수 갱신이 그 선수의 라인 배정을 막아버린다.
	 */
	private void applyLaneResult(
			Map<Long, Map<Lane, PlayerLaneRating>> laneRatings,
			MatchParticipant participant,
			ScrimMatch match,
			TeamAverage opponent,
			boolean win,
			double offRoleFactor,
			List<RatingHistory> histories) {
		PlayerLaneRating laneRating = laneRatings
				.getOrDefault(participant.getPlayerId(), Map.of())
				.get(participant.getLane());
		if (laneRating == null) {
			return;
		}
		GlickoCalculator.Outcome outcome = GlickoCalculator.update(
				laneRating.getRating(), laneRating.getRd(), opponent.rating(), opponent.rd(), win);
		int before = laneRating.getRating();
		int rdBefore = laneRating.getRd();
		int after = damp(before, outcome.rawDelta(), offRoleFactor);

		laneRating.applyMatchResult(after, outcome.newRd(), win);
		histories.add(RatingHistory.ofMatch(
				participant.getPlayerId(),
				match.getRoomId(),
				match.getId(),
				RatingScope.LANE,
				participant.getLane(),
				before,
				after,
				rdBefore,
				outcome.newRd(),
				outcome.expectedScore(),
				participant.getOffRoleFactor(),
				participant.getAssignedFrom().name()));
	}

	/**
	 * 오프롤 점수 보호 (§4.3) — raw delta 에 계수를 곱해 오르든 내리든 <b>대칭으로</b> 줄인다.
	 * 음수로 내려가면 리더보드가 이상해지므로 0 에서 막는다.
	 */
	private int damp(int currentRating, double rawDelta, double offRoleFactor) {
		return Math.max(0, (int) Math.round(currentRating + rawDelta * offRoleFactor));
	}

	private Map<TeamSide, TeamAverage> teamAverages(
			List<MatchParticipant> participants,
			Map<Long, PlayerRating> ratings) {
		Map<TeamSide, List<PlayerRating>> bySide = new EnumMap<>(TeamSide.class);
		for (MatchParticipant participant : participants) {
			PlayerRating rating = ratings.get(participant.getPlayerId());
			if (rating != null) {
				// 잠긴 선수도 포함한다 (§4.2-B) — 갱신만 안 할 뿐 실제 실력 데이터다
				bySide.computeIfAbsent(participant.getSide(), side -> new ArrayList<>()).add(rating);
			}
		}
		Map<TeamSide, TeamAverage> averages = new EnumMap<>(TeamSide.class);
		bySide.forEach((side, sideRatings) -> averages.put(side, new TeamAverage(
				sideRatings.stream().mapToInt(PlayerRating::getRating).average().orElseThrow(),
				sideRatings.stream().mapToInt(PlayerRating::getRd).average().orElseThrow())));
		return averages;
	}

	/**
	 * 점수 행이 없는 선수(주로 Riot 계정 없이 들어온 게스트)는 여기서 기본 시드로 만든다.
	 * 안 만들면 그 선수만 점수 추적에서 통째로 빠져, 나중에 리더보드에 포함하기로 해도
	 * 지난 판들의 근거가 남지 않는다.
	 */
	private Map<Long, PlayerRating> loadOrSeedRatings(List<MatchParticipant> participants, Long roomId) {
		List<Long> playerIds = participants.stream().map(MatchParticipant::getPlayerId).distinct().toList();
		Map<Long, PlayerRating> ratings = new HashMap<>();
		ratingRepository.findAllByPlayerIdIn(playerIds)
				.forEach(rating -> ratings.put(rating.getPlayerId(), rating));

		RatingSeedCalculator.Seed seed = RatingSeedCalculator.defaultSeed();
		List<PlayerRating> seeded = playerIds.stream()
				.filter(playerId -> !ratings.containsKey(playerId))
				.map(playerId -> PlayerRating.seed(
						playerId, roomId, seed.rating(), seed.rd(), SeedSource.DEFAULT))
				.toList();
		ratingRepository.saveAll(seeded).forEach(rating -> ratings.put(rating.getPlayerId(), rating));
		return ratings;
	}

	private Map<Long, Map<Lane, PlayerLaneRating>> loadLaneRatings(List<MatchParticipant> participants) {
		List<Long> playerIds = participants.stream().map(MatchParticipant::getPlayerId).distinct().toList();
		Map<Long, Map<Lane, PlayerLaneRating>> byPlayer = new HashMap<>();
		laneRatingRepository.findByPlayerIdIn(playerIds).forEach(laneRating -> byPlayer
				.computeIfAbsent(laneRating.getPlayerId(), playerId -> new EnumMap<>(Lane.class))
				.put(laneRating.getLane(), laneRating));
		return byPlayer;
	}

	private TeamSide opposite(TeamSide side) {
		return side == TeamSide.BLUE ? TeamSide.RED : TeamSide.BLUE;
	}
}
