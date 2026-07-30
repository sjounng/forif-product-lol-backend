package com.scrim.lolscrim.domain.player;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class LaneProficiencyCalculatorTest {

	@Test
	void mainLaneGetsFive() {
		// 20판 중 미드 15판(75%) -> 주라인
		Map<Lane, Integer> games = new EnumMap<>(Lane.class);
		games.put(Lane.MID, 15);
		games.put(Lane.TOP, 5);

		Map<Lane, Integer> result = LaneProficiencyCalculator.recommend(games);

		assertThat(result.get(Lane.MID)).isEqualTo(5);
	}

	@Test
	void gradesByShareNotRawCount() {
		// 20판: MID 8(40%)=5, TOP 4(20%)=4, JUNGLE 2(10%)=3, ADC 1(5%)=2, SUPPORT 0=1
		Map<Lane, Integer> games = new EnumMap<>(Lane.class);
		games.put(Lane.MID, 8);
		games.put(Lane.TOP, 4);
		games.put(Lane.JUNGLE, 2);
		games.put(Lane.ADC, 1);

		Map<Lane, Integer> result = LaneProficiencyCalculator.recommend(games);

		assertThat(result.get(Lane.MID)).isEqualTo(5);
		assertThat(result.get(Lane.TOP)).isEqualTo(4);
		assertThat(result.get(Lane.JUNGLE)).isEqualTo(3);
		assertThat(result.get(Lane.ADC)).isEqualTo(2);
		assertThat(result.get(Lane.SUPPORT)).isEqualTo(1);
	}

	@Test
	void neverAutoAssignsZeroSoNoLaneGetsBanned() {
		// 안 간 라인도 0(배정 금지)이 아니라 1이어야 한다 — 자동으로 라인을 막지 않는다
		Map<Lane, Integer> games = new EnumMap<>(Lane.class);
		games.put(Lane.MID, 20);

		Map<Lane, Integer> result = LaneProficiencyCalculator.recommend(games);

		assertThat(result.values()).doesNotContain(0);
		assertThat(result.get(Lane.SUPPORT)).isEqualTo(1);
	}

	@Test
	void allLanesPresentInResult() {
		Map<Lane, Integer> result = LaneProficiencyCalculator.recommend(Map.of(Lane.TOP, 3));
		assertThat(result).containsOnlyKeys(Lane.values());
	}

	@Test
	void realMeasuredDistributionProducesSensiblePool() {
		// 실측(Riot match-v5, hide on bush#KR1 최근 솔랭 20판): MIDDLE 14, JUNGLE 6
		Map<Lane, Integer> games = new EnumMap<>(Lane.class);
		games.put(Lane.MID, 14);    // 70%
		games.put(Lane.JUNGLE, 6);  // 30%

		Map<Lane, Integer> result = LaneProficiencyCalculator.recommend(games);

		assertThat(result.get(Lane.MID)).isEqualTo(5);     // 주라인
		assertThat(result.get(Lane.JUNGLE)).isEqualTo(4);  // 부라인
		assertThat(result.get(Lane.TOP)).isEqualTo(1);
		assertThat(result.get(Lane.ADC)).isEqualTo(1);
		assertThat(result.get(Lane.SUPPORT)).isEqualTo(1);
	}

	@Test
	void noMatchDataFallsBackToOneEverywhere() {
		// 신규 계정·조회 실패: 전 라인 1 (막지도 않고 우대하지도 않음)
		Map<Lane, Integer> result = LaneProficiencyCalculator.recommend(Map.of());
		assertThat(result).containsOnlyKeys(Lane.values());
		assertThat(result.values()).containsOnly(1);
	}
}
