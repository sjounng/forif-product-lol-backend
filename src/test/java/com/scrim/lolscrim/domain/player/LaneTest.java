package com.scrim.lolscrim.domain.player;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LaneTest {

	@Test
	void mapsRiotTeamPositionNames() {
		// Riot 은 MIDDLE/BOTTOM/UTILITY 라는 다른 이름을 쓴다
		assertThat(Lane.fromRiotTeamPosition("TOP")).isEqualTo(Lane.TOP);
		assertThat(Lane.fromRiotTeamPosition("JUNGLE")).isEqualTo(Lane.JUNGLE);
		assertThat(Lane.fromRiotTeamPosition("MIDDLE")).isEqualTo(Lane.MID);
		assertThat(Lane.fromRiotTeamPosition("BOTTOM")).isEqualTo(Lane.ADC);
		assertThat(Lane.fromRiotTeamPosition("UTILITY")).isEqualTo(Lane.SUPPORT);
	}

	@Test
	void returnsNullForRemakeOrUnknown() {
		// 리메이크 등에서 teamPosition 이 빈 문자열로 온다 (실측)
		assertThat(Lane.fromRiotTeamPosition("")).isNull();
		assertThat(Lane.fromRiotTeamPosition(null)).isNull();
		assertThat(Lane.fromRiotTeamPosition("AFK")).isNull();
	}
}
