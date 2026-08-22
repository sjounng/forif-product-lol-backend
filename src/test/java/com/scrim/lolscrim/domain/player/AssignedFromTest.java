package com.scrim.lolscrim.domain.player;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class AssignedFromTest {

	@Test
	void factorsMatchDesignTable() {
		// DESIGN §4.3 — 숫자를 바꾸면 DESIGN.md 도 같이 고쳐야 한다
		assertThat(AssignedFrom.PRIMARY.offRoleFactor()).isEqualByComparingTo(new BigDecimal("1.000"));
		assertThat(AssignedFrom.SECONDARY.offRoleFactor()).isEqualByComparingTo(new BigDecimal("0.950"));
		assertThat(AssignedFrom.FILL.offRoleFactor()).isEqualByComparingTo(new BigDecimal("0.900"));
		assertThat(AssignedFrom.OFF_ROLE.offRoleFactor()).isEqualByComparingTo(new BigDecimal("0.850"));
	}

	@Test
	void assignedToPreferredLanes() {
		assertThat(AssignedFrom.derive(Lane.MID, Lane.MID, Lane.JUNGLE)).isEqualTo(AssignedFrom.PRIMARY);
		assertThat(AssignedFrom.derive(Lane.JUNGLE, Lane.MID, Lane.JUNGLE)).isEqualTo(AssignedFrom.SECONDARY);
		assertThat(AssignedFrom.derive(Lane.SUPPORT, Lane.MID, Lane.JUNGLE)).isEqualTo(AssignedFrom.OFF_ROLE);
	}

	@Test
	void noPrimaryLaneMeansFill() {
		// primaryLane 이 비었으면 "아무거나" 를 고른 것으로 본다
		assertThat(AssignedFrom.derive(Lane.TOP, null, null)).isEqualTo(AssignedFrom.FILL);
	}

	@Test
	void secondaryStillWinsOverFill() {
		// 주라인이 FILL 이어도 부라인을 지정해두고 거기에 배정됐다면 SECONDARY 다
		assertThat(AssignedFrom.derive(Lane.ADC, null, Lane.ADC)).isEqualTo(AssignedFrom.SECONDARY);
		assertThat(AssignedFrom.derive(Lane.TOP, null, Lane.ADC)).isEqualTo(AssignedFrom.FILL);
	}

	@Test
	void offRoleDampensBothDirections() {
		// §4.3-A: raw delta -30 이면 실제 반영은 -25.5. 지는 쪽만 깎아주는 게 아니라 양방향으로 줄인다.
		double factor = AssignedFrom.OFF_ROLE.offRoleFactor().doubleValue();
		assertThat(-30 * factor).isEqualTo(-25.5);
		assertThat(30 * factor).isEqualTo(25.5);
	}
}
