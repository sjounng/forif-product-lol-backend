package com.scrim.lolscrim.domain.player;

/**
 * 라인. DB enum('TOP','JUNGLE','MID','ADC','SUPPORT') 과 이름이 일치해야 한다.
 * Riot 의 teamPosition 은 MIDDLE/BOTTOM/UTILITY 라는 다른 이름을 쓰므로 {@link #fromRiotTeamPosition} 로 변환한다.
 */
public enum Lane {
	TOP, JUNGLE, MID, ADC, SUPPORT;

	/**
	 * Riot match-v5 의 participant.teamPosition 을 우리 Lane 으로 변환한다.
	 * 리메이크·비정상 종료 판은 빈 문자열이 오므로 그때는 null 을 준다.
	 */
	public static Lane fromRiotTeamPosition(String teamPosition) {
		if (teamPosition == null || teamPosition.isBlank()) {
			return null;
		}
		return switch (teamPosition) {
			case "TOP" -> TOP;
			case "JUNGLE" -> JUNGLE;
			case "MIDDLE" -> MID;
			case "BOTTOM" -> ADC;
			case "UTILITY" -> SUPPORT;
			default -> null;
		};
	}
}
