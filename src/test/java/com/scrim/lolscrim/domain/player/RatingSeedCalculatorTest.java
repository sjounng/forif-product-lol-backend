package com.scrim.lolscrim.domain.player;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RatingSeedCalculatorTest {

	@Test
	void goldTwoZeroLpMatchesDesignExample() {
		// DESIGN.md §4.1-B: ladder_score 1400 -> seed_rating 1500, RD 200
		RatingSeedCalculator.Seed seed = RatingSeedCalculator.fromLadderScore(1400);
		assertThat(seed.rating()).isEqualTo(1500);
		assertThat(seed.rd()).isEqualTo(200);
	}

	@Test
	void defaultSeedIsFifteenHundredWithFullUncertainty() {
		RatingSeedCalculator.Seed seed = RatingSeedCalculator.defaultSeed();
		assertThat(seed.rating()).isEqualTo(1500);
		assertThat(seed.rd()).isEqualTo(350);
	}

	@Test
	void mainLaneKeepsBaseRdButUnplayedLaneStaysUncertain() {
		// §4.1-C: 안 가본 라인까지 낮은 RD 를 주면 그 라인이 가장 느리게 교정된다 (방향이 반대)
		int baseRd = 200; // 랭크 조회 성공 시
		assertThat(RatingSeedCalculator.laneRd(baseRd, 5)).isEqualTo(200); // 주라인
		assertThat(RatingSeedCalculator.laneRd(baseRd, 4)).isEqualTo(200); // 부라인
		assertThat(RatingSeedCalculator.laneRd(baseRd, 1)).isEqualTo(350); // 최근에 간 적 없음
		assertThat(RatingSeedCalculator.laneRd(baseRd, 0)).isEqualTo(350); // 배정 금지
	}

	@Test
	void occasionalLaneSitsBetween() {
		int baseRd = 200;
		int rd = RatingSeedCalculator.laneRd(baseRd, 3);
		assertThat(rd).isGreaterThan(baseRd).isLessThan(350);
	}

	@Test
	void laneRdNeverExceedsGlickoDefault() {
		// 조회 실패로 baseRd 가 이미 350인 경우, 어떤 lanePool 이어도 350을 넘지 않아야 한다
		for (int pool = 0; pool <= 5; pool++) {
			assertThat(RatingSeedCalculator.laneRd(350, pool)).isEqualTo(350);
		}
	}
}
