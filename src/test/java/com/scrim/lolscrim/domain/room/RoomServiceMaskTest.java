package com.scrim.lolscrim.domain.room;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RoomServiceMaskTest {

	@Test
	void keepsFirstTwoAndLastCharacter() {
		// 회귀: char + char 는 문자열 연결이 아니라 정수 덧셈이라 예전엔 "150*****3" 이 나왔다
		assertThat(RoomService.maskEntryCode("SCRIM123")).isEqualTo("SC*****3");
		assertThat(RoomService.maskEntryCode("test1234")).isEqualTo("te*****4");
		assertThat(RoomService.maskEntryCode("A1B2C5")).isEqualTo("A1***5");
	}

	@Test
	void maskedLengthMatchesOriginal() {
		for (String code : new String[] {"ABCDE", "SCRIM123", "a-very-long-entry-code"}) {
			assertThat(RoomServiceMaskTest.mask(code)).hasSameSizeAs(code);
		}
	}

	@Test
	void neverLeaksAnyOriginalCharacterWhenTooShort() {
		// 짧은 코드는 앞2+뒤1을 남기면 원문이 거의 그대로 드러나므로 통째로 가린다
		assertThat(RoomService.maskEntryCode("A")).isEqualTo("*");
		assertThat(RoomService.maskEntryCode("AB")).isEqualTo("**");
		assertThat(RoomService.maskEntryCode("ABC")).isEqualTo("***");
		assertThat(RoomService.maskEntryCode("ABCD")).isEqualTo("****");
	}

	@Test
	void masksAtLeastOneCharacterInTheMiddle() {
		assertThat(RoomService.maskEntryCode("ABCDE")).isEqualTo("AB**E");
	}

	private static String mask(String code) {
		return RoomService.maskEntryCode(code);
	}
}
