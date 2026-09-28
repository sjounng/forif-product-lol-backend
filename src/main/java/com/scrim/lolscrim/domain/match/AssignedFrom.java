package com.scrim.lolscrim.domain.match;

import com.scrim.lolscrim.domain.player.Lane;

import java.math.BigDecimal;

/**
 * 배정된 라인이 그 선수의 선호와 얼마나 맞았는지. DESIGN.md §4.3.
 *
 * 여기서 나오는 계수를 Glicko raw delta 에 곱해서, 오프롤로 뛴 판은 <b>오르든 내리든</b> 흔들림을 줄인다.
 * DB 는 {@code match_participants.assigned_from} ENUM 과 이름이 일치해야 한다.
 */
public enum AssignedFrom {

	PRIMARY("1.000"),
	SECONDARY("0.950"),
	FILL("0.900"),
	OFF_ROLE("0.850");

	private final BigDecimal offRoleFactor;

	AssignedFrom(String offRoleFactor) {
		this.offRoleFactor = new BigDecimal(offRoleFactor);
	}

	/** DECIMAL(4,3) 컬럼과 스케일을 맞춘 계수. */
	public BigDecimal offRoleFactor() {
		return offRoleFactor;
	}

	/**
	 * 배정 라인을 선호 라인과 비교해 매치 생성 시점에 파생시킨다.
	 *
	 * <p>판정 순서는 DESIGN §4.3 을 그대로 따른다 — primaryLane 이 FILL("아무거나")이어도
	 * secondaryLane 이 지정돼 있고 거기에 배정됐다면 FILL 이 아니라 SECONDARY 다.
	 *
	 * @param assignedLane  이번 판에 실제로 배정된 라인
	 * @param primaryLane   주라인. {@code null} 이면 "아무거나"(FILL)를 고른 것으로 본다
	 * @param secondaryLane 부라인. 없으면 {@code null}
	 */
	public static AssignedFrom derive(Lane assignedLane, Lane primaryLane, Lane secondaryLane) {
		if (assignedLane != null && assignedLane == primaryLane) {
			return PRIMARY;
		}
		if (assignedLane != null && assignedLane == secondaryLane) {
			return SECONDARY;
		}
		if (primaryLane == null) {
			return FILL;
		}
		return OFF_ROLE;
	}
}
