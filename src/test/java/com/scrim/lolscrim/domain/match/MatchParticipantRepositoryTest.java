package com.scrim.lolscrim.domain.match;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.util.ReflectionTestUtils;

import com.scrim.lolscrim.domain.player.Lane;
import com.scrim.lolscrim.domain.session.TeamSide;

@DataJpaTest(properties = {
		"spring.flyway.enabled=false",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"spring.datasource.url=jdbc:h2:mem:champion-analytics;MODE=MySQL;DB_CLOSE_DELAY=-1",
		"spring.datasource.driver-class-name=org.h2.Driver"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MatchParticipantRepositoryTest {

	@Autowired
	private MatchParticipantRepository matchParticipantRepository;

	@Autowired
	private ScrimMatchRepository scrimMatchRepository;

	@Test
	void aggregatesCompletedMatchesAndCountsOnlyMatchesWithRecordedChampions() {
		ScrimMatch completedWithChampions = completedMatch(1, TeamSide.BLUE);
		ScrimMatch completedWithoutChampion = completedMatch(2, TeamSide.RED);
		ScrimMatch liveMatch = liveMatch(3);

		MatchParticipant garen = participant(
				completedWithChampions.getId(), 1L, TeamSide.BLUE, Lane.TOP, 86);
		garen.recordResult(TeamSide.BLUE);
		garen.recordKda(4, 0, 6);
		MatchParticipant ahri = participant(
				completedWithChampions.getId(), 2L, TeamSide.RED, Lane.MID, 103);
		ahri.recordResult(TeamSide.BLUE);
		MatchParticipant noChampion = participant(
				completedWithoutChampion.getId(), 3L, TeamSide.BLUE, Lane.JUNGLE, null);
		noChampion.recordResult(TeamSide.RED);
		MatchParticipant liveGaren = participant(
				liveMatch.getId(), 4L, TeamSide.BLUE, Lane.TOP, 86);
		liveGaren.recordResult(TeamSide.BLUE);
		liveGaren.recordKda(20, 1, 10);
		matchParticipantRepository.saveAllAndFlush(List.of(garen, ahri, noChampion, liveGaren));

		long totalMatches = matchParticipantRepository
				.countMatchesWithChampionByMatchStatus(MatchStatus.COMPLETED);
		Map<Integer, ChampionLaneAnalyticsProjection> rows = matchParticipantRepository
				.aggregateChampionAnalyticsByMatchStatus(MatchStatus.COMPLETED)
				.stream()
				.collect(Collectors.toMap(ChampionLaneAnalyticsProjection::getChampionId, Function.identity()));

		assertThat(totalMatches).isEqualTo(1L);
		assertThat(rows).containsOnlyKeys(86, 103);
		assertThat(rows.get(86).getLane()).isEqualTo(Lane.TOP);
		assertThat(rows.get(86).getPicks()).isEqualTo(1L);
		assertThat(rows.get(86).getWins()).isEqualTo(1L);
		assertThat(rows.get(86).getKdaSum()).isEqualTo(10.0);
		assertThat(rows.get(86).getKdaSamples()).isEqualTo(1L);
		assertThat(rows.get(103).getLane()).isEqualTo(Lane.MID);
		assertThat(rows.get(103).getWins()).isZero();
		assertThat(rows.get(103).getKdaSum()).isZero();
		assertThat(rows.get(103).getKdaSamples()).isZero();
	}

	private ScrimMatch completedMatch(int gameNo, TeamSide winnerSide) {
		LocalDateTime now = LocalDateTime.of(2026, 8, 24, 12, gameNo);
		ScrimMatch match = ScrimMatch.createDrafting(1L, 1L, gameNo, now);
		match.proposeResult(10L, winnerSide, null, now);
		match.complete(now);
		return scrimMatchRepository.saveAndFlush(match);
	}

	private ScrimMatch liveMatch(int gameNo) {
		LocalDateTime now = LocalDateTime.of(2026, 8, 24, 13, gameNo);
		ScrimMatch match = ScrimMatch.createDrafting(2L, 1L, gameNo, now);
		match.start(now);
		return scrimMatchRepository.saveAndFlush(match);
	}

	private MatchParticipant participant(
			Long matchId,
			Long playerId,
			TeamSide side,
			Lane lane,
			Integer championId) {
		MatchParticipant participant = new MatchParticipant();
		ReflectionTestUtils.setField(participant, "matchId", matchId);
		ReflectionTestUtils.setField(participant, "playerId", playerId);
		ReflectionTestUtils.setField(participant, "roomId", 1L);
		ReflectionTestUtils.setField(participant, "side", side);
		ReflectionTestUtils.setField(participant, "lane", lane);
		// assignedFrom 이 String 에서 AssignedFrom enum 으로 바뀌었다 (오프롤 계수를 enum 이 들고 있다)
		ReflectionTestUtils.setField(participant, "assignedFrom", AssignedFrom.PRIMARY);
		ReflectionTestUtils.setField(participant, "offRoleFactor", AssignedFrom.PRIMARY.offRoleFactor());
		if (championId != null) {
			participant.assignChampion(championId);
		}
		return participant;
	}
}
