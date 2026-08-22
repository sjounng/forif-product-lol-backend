package com.scrim.lolscrim.domain.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class GlickoCalculatorTest {

	@Test
	void winnerGainsAndLoserLoses() {
		GlickoCalculator.Outcome won = GlickoCalculator.update(1500, 200, 1500, 200, true);
		GlickoCalculator.Outcome lost = GlickoCalculator.update(1500, 200, 1500, 200, false);
		assertThat(won.rawDelta()).isPositive();
		assertThat(lost.rawDelta()).isNegative();
	}

	@Test
	void evenMatchupIsSymmetric() {
		// 실력·불확실성이 같으면 E = 0.5 라 이겼을 때 오르는 폭과 졌을 때 내리는 폭이 같아야 한다
		GlickoCalculator.Outcome won = GlickoCalculator.update(1500, 200, 1500, 200, true);
		GlickoCalculator.Outcome lost = GlickoCalculator.update(1500, 200, 1500, 200, false);
		assertThat(won.rawDelta()).isCloseTo(-lost.rawDelta(), within(1e-9));
	}

	@Test
	void higherRdMovesMore() {
		// §4.2 의 핵심: rd 가 클수록 한 판의 영향(K)이 커진다. 고정 K Elo 대신 Glicko 를 쓴 이유다.
		double uncertain = GlickoCalculator.update(1500, 350, 1500, 200, true).rawDelta();
		double settled = GlickoCalculator.update(1500, 50, 1500, 200, true).rawDelta();
		assertThat(uncertain).isGreaterThan(settled);
	}

	@Test
	void beatingStrongerTeamGainsMoreThanBeatingWeakerTeam() {
		double upset = GlickoCalculator.update(1500, 200, 1900, 200, true).rawDelta();
		double expected = GlickoCalculator.update(1500, 200, 1100, 200, true).rawDelta();
		assertThat(upset).isGreaterThan(expected);
	}

	@Test
	void rdShrinksAfterPlaying() {
		// 한 판 뛰면 그 선수에 대해 더 알게 되므로 불확실성은 줄어든다
		GlickoCalculator.Outcome outcome = GlickoCalculator.update(1500, 350, 1500, 350, true);
		assertThat(outcome.newRd()).isLessThan(350);
	}

	@Test
	void rdNeverFallsBelowFloor() {
		// 바닥이 없으면 RD 가 0 으로 수렴해 점수가 얼어붙는다 (§4.2-A)
		GlickoCalculator.Outcome outcome = GlickoCalculator.update(1500, GlickoCalculator.MIN_RD, 1500, 200, true);
		assertThat(outcome.newRd()).isGreaterThanOrEqualTo(GlickoCalculator.MIN_RD);
	}

	@Test
	void extremeRatingGapDoesNotBlowUp() {
		// E 가 0/1 에 붙으면 d² 가 발산한다. 무한대·NaN 이 점수로 새어나오면 안 된다.
		GlickoCalculator.Outcome outcome = GlickoCalculator.update(4000, 200, 0, 200, false);
		assertThat(outcome.rawDelta()).isFinite();
		assertThat(outcome.newRd()).isPositive();
	}

	@Test
	void rdUpdateIsIndependentOfResult() {
		// 새 RD 는 기존 RD 와 d² 만으로 정해진다 — 이겼든 졌든 같아야 한다
		GlickoCalculator.Outcome won = GlickoCalculator.update(1500, 200, 1600, 250, true);
		GlickoCalculator.Outcome lost = GlickoCalculator.update(1500, 200, 1600, 250, false);
		assertThat(won.newRd()).isEqualTo(lost.newRd());
	}
}
