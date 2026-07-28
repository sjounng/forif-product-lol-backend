package com.scrim.lolscrim.domain.riot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class LadderScoreCalculatorTest {

	@Test
	void goldTwoZeroLpMatchesDesignExample() {
		// DESIGN.md §4.1: 골드2 0LP -> 1400
		assertThat(LadderScoreCalculator.calculate(Tier.GOLD, RankDivision.II, 0)).isEqualTo(1400);
	}

	@Test
	void ironFourZeroLpIsFloor() {
		assertThat(LadderScoreCalculator.calculate(Tier.IRON, RankDivision.IV, 0)).isZero();
	}

	@Test
	void challengerHighLpClampsAtFourThousand() {
		assertThat(LadderScoreCalculator.calculate(Tier.CHALLENGER, null, 5000)).isEqualTo(4000);
	}

	@Test
	void masterHasNoDivisionComponent() {
		// tier_index(MASTER)=7 -> 7*400=2800, LP 100 그대로 더해짐 (division 없음)
		assertThat(LadderScoreCalculator.calculate(Tier.MASTER, null, 100)).isEqualTo(2900);
	}

	@Test
	void masterIgnoresNonNullDivisionFromLegacyRiotField() {
		// 실측: Riot league-v4가 마스터 이상에도 rank="I"를 얹어서 준다. 무시해야 한다.
		// 7*400 + 0(무시) + 50 = 2850, division을 반영하면 3150이 되어 틀림
		assertThat(LadderScoreCalculator.calculate(Tier.MASTER, RankDivision.I, 50)).isEqualTo(2850);
	}

	@Test
	void rejectsUnranked() {
		assertThatThrownBy(() -> LadderScoreCalculator.calculate(Tier.UNRANKED, null, 0))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
