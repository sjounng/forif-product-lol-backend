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
}
