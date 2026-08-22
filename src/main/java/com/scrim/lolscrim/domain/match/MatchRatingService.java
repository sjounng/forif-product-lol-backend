package com.scrim.lolscrim.domain.match;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.scrim.lolscrim.domain.player.GlickoCalculator;
import com.scrim.lolscrim.domain.player.Lane;
import com.scrim.lolscrim.domain.player.PlayerLaneRating;
import com.scrim.lolscrim.domain.player.PlayerLaneRatingRepository;
import com.scrim.lolscrim.domain.player.PlayerRating;
import com.scrim.lolscrim.domain.player.PlayerRatingRepository;
import com.scrim.lolscrim.domain.player.RatingSeedCalculator;
import com.scrim.lolscrim.domain.player.SeedSource;
import com.scrim.lolscrim.domain.session.TeamSide;

import lombok.RequiredArgsConstructor;

/**
 * 확정된 내전 결과를 선수 점수에 반영한다. DESIGN.md §4.2 / §4.3.
 *
 * <p>솔랭 점수는 §4.1-B 로 <b>최초 시드</b>만 잡고, 그 뒤로는 이 클래스가 내전 결과로 점수를 굴린다.
 * 시드 로직({@link RatingSeedCalculator})과 갱신 로직({@link GlickoCalculator})이 갈라져 있는 이유다.
 */
@Service
@RequiredArgsConstructor
public class MatchRatingService {

	private final MatchParticipantRepository participantRepository;
	private final PlayerRatingRepository ratingRepository;
	private final PlayerLaneRatingRepository laneRatingRepository;

	/** 한 팀의 갱신 <b>전</b> 평균. 상대팀 기준값이라 계산 도중 변하면 안 된다. */
	private record TeamAverage(double rating, double rd) {
	}

	/**
	 * @param ratingEnabled 세션의 점수 반영 여부. 친선전(false)은 전적만 남고 점수는 안 움직인다.
	 */
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

		for (MatchParticipant participant : participants) {
			PlayerRating rating = ratings.get(participant.getPlayerId());
			TeamAverage opponent = averages.get(opposite(participant.getSide()));
			if (rating == null || opponent == null || rating.isLocked()) {
				continue;
			}
			boolean win = participant.getSide() == match.getWinnerSide();
			double offRoleFactor = participant.getOffRoleFactor().doubleValue();

			GlickoCalculator.Outcome outcome = GlickoCalculator.update(
					rating.getRating(), rating.getRd(), opponent.rating(), opponent.rd(), win);
			rating.applyMatchResult(
					damp(rating.getRating(), outcome.rawDelta(), offRoleFactor),
					outcome.newRd(),
					win,
					now);

			applyLaneResult(laneRatings, participant, opponent, win, offRoleFactor);
		}
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
			TeamAverage opponent,
			boolean win,
			double offRoleFactor) {
		PlayerLaneRating laneRating = laneRatings
				.getOrDefault(participant.getPlayerId(), Map.of())
				.get(participant.getLane());
		if (laneRating == null) {
			return;
		}
		GlickoCalculator.Outcome outcome = GlickoCalculator.update(
				laneRating.getRating(), laneRating.getRd(), opponent.rating(), opponent.rd(), win);
		laneRating.applyMatchResult(
				damp(laneRating.getRating(), outcome.rawDelta(), offRoleFactor),
				outcome.newRd(),
				win);
	}

	/**
	 * 오프롤 점수 보호 (§4.3) — raw delta 에 계수를 곱해 오르든 내리든 흔들림을 줄인다.
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
	 * 안 만들면 그 선수만 영원히 무점수로 남아 리더보드에서 빠진다.
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
