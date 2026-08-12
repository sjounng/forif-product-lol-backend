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
	void masterStartsRightAboveDiamondOne() {
		// 다이아 I 99LP 와 마스터 0LP 사이가 끊기면 안 된다
		assertThat(LadderScoreCalculator.calculate(Tier.DIAMOND, RankDivision.I, 99)).isEqualTo(2799);
		assertThat(LadderScoreCalculator.calculate(Tier.MASTER, null, 0)).isEqualTo(2800);
	}

	@Test
	void apexLpReachesCeilingOnlyAtTheTop() {
		assertThat(LadderScoreCalculator.calculate(Tier.CHALLENGER, null, 3000)).isEqualTo(4000);
		assertThat(LadderScoreCalculator.calculate(Tier.CHALLENGER, null, 9999)).isEqualTo(4000);
	}

	@Test
	void apexIsOrderedByLpAlone() {
		// 회귀: 예전엔 tier_index*400 을 더한 뒤 4000 으로 클램프해서, LP 400 이 넘는 챌린저가
		// 전부 4000 으로 뭉개졌다 (실측 KR 챌린저 LP 1901~2650 이 전부 동점 -> 팀 밸런싱 불가).
		int lowChallenger = LadderScoreCalculator.calculate(Tier.CHALLENGER, null, 1901);
		int topChallenger = LadderScoreCalculator.calculate(Tier.CHALLENGER, null, 2650);
		assertThat(lowChallenger).isLessThan(topChallenger);
		assertThat(topChallenger).isLessThan(4000);

		// Master/GM/Challenger 는 하나의 LP 풀을 공유하므로 같은 LP 면 같은 점수여야 한다
		assertThat(LadderScoreCalculator.calculate(Tier.GRANDMASTER, null, 1000))
				.isEqualTo(LadderScoreCalculator.calculate(Tier.CHALLENGER, null, 1000));
	}

	@Test
	void apexIsStrictlyIncreasingAcrossRealisticLpRange() {
		int previous = -1;
		for (int lp = 0; lp <= 2900; lp += 100) {
			int score = LadderScoreCalculator.calculate(Tier.MASTER, null, lp);
			assertThat(score).isGreaterThan(previous);
			assertThat(score).isBetween(2800, 4000);
			previous = score;
		}
	}

	@Test
	void masterIgnoresNonNullDivisionFromLegacyRiotField() {
		// 실측: Riot league-v4가 마스터 이상에도 rank="I"를 얹어서 준다. 무시해야 한다.
		assertThat(LadderScoreCalculator.calculate(Tier.MASTER, RankDivision.I, 50))
				.isEqualTo(LadderScoreCalculator.calculate(Tier.MASTER, null, 50));
	}

	@Test
	void rejectsUnranked() {
		assertThatThrownBy(() -> LadderScoreCalculator.calculate(Tier.UNRANKED, null, 0))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
