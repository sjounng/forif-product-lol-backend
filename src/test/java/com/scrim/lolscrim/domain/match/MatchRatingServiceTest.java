package com.scrim.lolscrim.domain.match;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.scrim.lolscrim.domain.player.Lane;
import com.scrim.lolscrim.domain.player.PlayerLaneRating;
import com.scrim.lolscrim.domain.player.PlayerLaneRatingRepository;
import com.scrim.lolscrim.domain.player.PlayerRating;
import com.scrim.lolscrim.domain.player.PlayerRatingRepository;
import com.scrim.lolscrim.domain.player.RatingHistory;
import com.scrim.lolscrim.domain.player.RatingHistoryRepository;
import com.scrim.lolscrim.domain.player.RatingScope;
import com.scrim.lolscrim.domain.player.SeedSource;
import com.scrim.lolscrim.domain.session.SessionTeamMember;
import com.scrim.lolscrim.domain.session.TeamSide;

@ExtendWith(MockitoExtension.class)
class MatchRatingServiceTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 11, 21, 0);
	private static final long MATCH_ID = 50L;
	private static final long ROOM_ID = 9L;
	private static final Lane[] LANES = { Lane.TOP, Lane.JUNGLE, Lane.MID, Lane.ADC, Lane.SUPPORT };

	@Mock
	private MatchParticipantRepository participantRepository;
	@Mock
	private PlayerRatingRepository ratingRepository;
	@Mock
	private PlayerLaneRatingRepository laneRatingRepository;
	@Mock
	private RatingHistoryRepository historyRepository;

	private MatchRatingService service;

	@BeforeEach
	void setUp() {
		service = new MatchRatingService(
				participantRepository, ratingRepository, laneRatingRepository, historyRepository);
	}

	@Test
	void winnersGainAndLosersLose() {
		List<PlayerRating> ratings = evenRatings();
		stub(participants(), ratings, List.of());

		service.apply(completedMatch(TeamSide.BLUE), true, NOW);

		// playerId 1~5 = BLUE(승), 6~10 = RED(패)
		assertThat(ratings.subList(0, 5)).allSatisfy(rating -> assertThat(rating.getRating()).isGreaterThan(1500));
		assertThat(ratings.subList(5, 10)).allSatisfy(rating -> assertThat(rating.getRating()).isLessThan(1500));
	}

	@Test
	void recordsWinLossAndStreak() {
		List<PlayerRating> ratings = evenRatings();
		stub(participants(), ratings, List.of());

		service.apply(completedMatch(TeamSide.BLUE), true, NOW);

		PlayerRating winner = ratings.getFirst();
		assertThat(winner.getGamesPlayed()).isEqualTo(1);
		assertThat(winner.getWins()).isEqualTo(1);
		assertThat(winner.getWinStreak()).isEqualTo((short) 1);
		assertThat(winner.getLastPlayedAt()).isEqualTo(NOW);

		PlayerRating loser = ratings.get(5);
		assertThat(loser.getLosses()).isEqualTo(1);
		assertThat(loser.getWinStreak()).isEqualTo((short) -1);
	}

	@Test
	void appliesExactlyOncePerMatch() {
		List<PlayerRating> ratings = evenRatings();
		stub(participants(), ratings, List.of());
		ScrimMatch match = completedMatch(TeamSide.BLUE);

		service.apply(match, true, NOW);
		int afterFirst = ratings.getFirst().getRating();
		service.apply(match, true, NOW);

		assertThat(match.isRatingApplied()).isTrue();
		assertThat(ratings.getFirst().getRating()).isEqualTo(afterFirst);
		assertThat(ratings.getFirst().getGamesPlayed()).isEqualTo(1);
	}

	@Test
	void friendlySessionLeavesRatingsUntouched() {
		List<PlayerRating> ratings = evenRatings();
		ScrimMatch match = completedMatch(TeamSide.BLUE);

		service.apply(match, false, NOW);

		assertThat(match.isRatingApplied()).isFalse();
		assertThat(ratings).allSatisfy(rating -> assertThat(rating.getRating()).isEqualTo(1500));
	}

	@Test
	void lockedRatingIsNotTouched() {
		List<PlayerRating> ratings = evenRatings();
		PlayerRating locked = ratings.getFirst();
		ReflectionTestUtils.setField(locked, "locked", true);
		stub(participants(), ratings, List.of());

		service.apply(completedMatch(TeamSide.BLUE), true, NOW);

		assertThat(locked.getRating()).isEqualTo(1500);
		assertThat(locked.getGamesPlayed()).isZero();
		// 같은 팀의 잠기지 않은 선수는 정상 갱신된다
		assertThat(ratings.get(1).getRating()).isGreaterThan(1500);
	}

	@Test
	void lockedPlayerStillCountsTowardOpposingTeamAverage() {
		// §4.2-B: 잠긴 선수는 갱신만 빠지고 상대팀 평균 계산에는 들어간다.
		// 전원 동점으로 두면 그 선수가 평균에서 빠져도 평균이 그대로라 규칙을 판별하지 못한다 —
		// 잠긴 선수만 점수를 크게 다르게 줘서 상대팀 델타가 그 값에 반응하는지 본다.
		List<PlayerRating> withLocked = evenRatings();
		PlayerRating locked = withLocked.getFirst();
		ReflectionTestUtils.setField(locked, "locked", true);
		ReflectionTestUtils.setField(locked, "rating", 3000);
		stub(participants(), withLocked, List.of());
		service.apply(completedMatch(TeamSide.BLUE), true, NOW);
		int redDeltaWithStrongLockedOpponent = withLocked.get(5).getRating() - 1500;

		// 같은 배치인데 잠긴 선수가 평범한 점수인 경우
		List<PlayerRating> baseline = evenRatings();
		PlayerRating lockedBaseline = baseline.getFirst();
		ReflectionTestUtils.setField(lockedBaseline, "locked", true);
		stub(participants(), baseline, List.of());
		service.apply(completedMatch(TeamSide.BLUE), true, NOW);
		int redDeltaWithAverageLockedOpponent = baseline.get(5).getRating() - 1500;

		// 상대에 강한 선수가 있었으니 패배 손실이 더 작아야 한다 (둘 다 음수)
		assertThat(redDeltaWithStrongLockedOpponent).isGreaterThan(redDeltaWithAverageLockedOpponent);
	}

	@Test
	void recordsAuditTrailForOverallAndLane() {
		// §4.2-B — 점수는 파괴적으로 갱신되므로 되돌릴 근거를 남겨야 한다
		List<PlayerRating> ratings = evenRatings();
		PlayerLaneRating played = PlayerLaneRating.seed(1L, Lane.TOP, ROOM_ID, 1500, 200, 5);
		stub(participants(), ratings, List.of(played));

		service.apply(completedMatch(TeamSide.BLUE), true, NOW);

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<RatingHistory>> captor = ArgumentCaptor.forClass(List.class);
		verify(historyRepository).saveAll(captor.capture());
		List<RatingHistory> histories = captor.getValue();

		// 10명의 OVERALL + playerId 1 의 LANE 1건
		assertThat(histories).hasSize(11);
		RatingHistory overall = histories.stream()
				.filter(history -> history.getScope() == RatingScope.OVERALL)
				.filter(history -> history.getPlayerId().equals(1L))
				.findFirst()
				.orElseThrow();
		assertThat(overall.getRatingBefore()).isEqualTo(1500);
		assertThat(overall.getRatingAfter()).isGreaterThan(1500);
		assertThat(overall.getDelta()).isEqualTo(overall.getRatingAfter() - 1500);
		assertThat(overall.getMatchId()).isEqualTo(MATCH_ID);
		assertThat(overall.getExpectedScore()).isNotNull();
		assertThat(overall.getRdBefore()).isEqualTo((short) 200);

		assertThat(histories).anySatisfy(history -> {
			assertThat(history.getScope()).isEqualTo(RatingScope.LANE);
			assertThat(history.getLane()).isEqualTo(Lane.TOP);
		});
	}

	@Test
	void writesRatingSnapshotOntoParticipant() {
		List<MatchParticipant> participants = participants();
		List<PlayerRating> ratings = evenRatings();
		stub(participants, ratings, List.of());

		service.apply(completedMatch(TeamSide.BLUE), true, NOW);

		MatchParticipant winner = participants.getFirst();
		assertThat(winner.getRatingBefore()).isEqualTo(1500);
		assertThat(winner.getRatingAfter()).isGreaterThan(1500);
		assertThat(winner.getRatingDelta())
				.isEqualTo(winner.getRatingAfter() - winner.getRatingBefore());
	}

	@Test
	void offRoleMovesLessThanPrimary() {
		// §4.3 — 같은 팀, 같은 점수인데 배정만 다르면 오프롤 쪽이 덜 움직여야 한다
		List<MatchParticipant> participants = participants();
		ReflectionTestUtils.setField(participants.get(1), "assignedFrom", AssignedFrom.OFF_ROLE);
		ReflectionTestUtils.setField(
				participants.get(1), "offRoleFactor", AssignedFrom.OFF_ROLE.offRoleFactor());
		List<PlayerRating> ratings = evenRatings();
		stub(participants, ratings, List.of());

		service.apply(completedMatch(TeamSide.BLUE), true, NOW);

		int primaryGain = ratings.getFirst().getRating() - 1500;
		int offRoleGain = ratings.get(1).getRating() - 1500;
		assertThat(offRoleGain).isPositive().isLessThan(primaryGain);
	}

	@Test
	void onlyThePlayedLaneIsUpdated() {
		List<PlayerRating> ratings = evenRatings();
		PlayerLaneRating played = PlayerLaneRating.seed(1L, Lane.TOP, ROOM_ID, 1500, 200, 5);
		PlayerLaneRating untouched = PlayerLaneRating.seed(1L, Lane.SUPPORT, ROOM_ID, 1500, 200, 2);
		stub(participants(), ratings, List.of(played, untouched));

		service.apply(completedMatch(TeamSide.BLUE), true, NOW);

		// playerId 1 은 TOP 으로 배정됐다
		assertThat(played.getRating()).isGreaterThan(1500);
		assertThat(played.getGamesPlayed()).isEqualTo(1);
		assertThat(untouched.getRating()).isEqualTo(1500);
		assertThat(untouched.getGamesPlayed()).isZero();
	}

	@Test
	void seedsMissingRatingSoGuestsStillRank() {
		// 점수 행이 없는 게스트가 끼어 있어도 그 판으로 시드가 생겨야 한다
		List<PlayerRating> existing = new ArrayList<>(evenRatings().subList(0, 9));
		when(participantRepository.findAllByMatchId(MATCH_ID)).thenReturn(participants());
		when(ratingRepository.findAllByPlayerIdIn(org.mockito.ArgumentMatchers.anyList()))
				.thenReturn(existing);
		when(ratingRepository.saveAll(org.mockito.ArgumentMatchers.anyList()))
				.thenAnswer(invocation -> invocation.getArgument(0));
		when(laneRatingRepository.findByPlayerIdIn(org.mockito.ArgumentMatchers.anyList()))
				.thenReturn(List.of());

		service.apply(completedMatch(TeamSide.BLUE), true, NOW);

		assertThat(existing).allSatisfy(rating -> assertThat(rating.getGamesPlayed()).isEqualTo(1));
	}

	private void stub(
			List<MatchParticipant> participants,
			List<PlayerRating> ratings,
			List<PlayerLaneRating> laneRatings) {
		when(participantRepository.findAllByMatchId(MATCH_ID)).thenReturn(participants);
		when(ratingRepository.findAllByPlayerIdIn(org.mockito.ArgumentMatchers.anyList()))
				.thenReturn(ratings);
		when(laneRatingRepository.findByPlayerIdIn(org.mockito.ArgumentMatchers.anyList()))
				.thenReturn(laneRatings);
	}

	/** 10명 전원 1500/200 — 실력차를 배제해야 배정·잠금 같은 다른 변수만 검증할 수 있다. */
	private List<PlayerRating> evenRatings() {
		List<PlayerRating> ratings = new ArrayList<>();
		for (long playerId = 1; playerId <= 10; playerId++) {
			ratings.add(PlayerRating.seed(playerId, ROOM_ID, 1500, 200, SeedSource.SOLO_RANK));
		}
		return ratings;
	}

	private List<MatchParticipant> participants() {
		List<MatchParticipant> participants = new ArrayList<>();
		for (int index = 0; index < 10; index++) {
			TeamSide side = index < 5 ? TeamSide.BLUE : TeamSide.RED;
			SessionTeamMember member = SessionTeamMember.create(
					7L, side, (long) (index + 1), LANES[index % 5], NOW);
			participants.add(MatchParticipant.from(
					MATCH_ID, ROOM_ID, member, side, AssignedFrom.PRIMARY));
		}
		return participants;
	}

	private ScrimMatch completedMatch(TeamSide winner) {
		ScrimMatch match = ScrimMatch.createDrafting(7L, ROOM_ID, 1, TeamSide.BLUE, NOW);
		ReflectionTestUtils.setField(match, "id", MATCH_ID);
		match.proposeResult(1L, winner, null, NOW);
		match.complete(NOW);
		return match;
	}
}
