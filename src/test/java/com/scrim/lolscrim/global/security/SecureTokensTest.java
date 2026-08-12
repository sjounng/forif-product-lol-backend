package com.scrim.lolscrim.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class SecureTokensTest {

	@Test
	void randomHexLengthIsTwiceTheByteCount() {
		// 저장 컬럼과 맞아야 한다: user_sessions.refresh_token_hash 는 CHAR(64) = 32바이트
		assertThat(SecureTokens.randomHex(32)).hasSize(64);
		assertThat(SecureTokens.randomHex(16)).hasSize(32);
	}

	@Test
	void randomHexIsLowercaseHexOnly() {
		assertThat(SecureTokens.randomHex(32)).matches("[0-9a-f]{64}");
	}

	@Test
	void randomHexDoesNotRepeat() {
		Set<String> seen = new HashSet<>();
		for (int i = 0; i < 500; i++) {
			assertThat(seen.add(SecureTokens.randomHex(32))).isTrue();
		}
	}

	@Test
	void randomHexRejectsNonPositiveLength() {
		assertThatThrownBy(() -> SecureTokens.randomHex(0))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void randomFromUsesOnlyTheGivenAlphabet() {
		// 그룹 공개 코드용 — 0/O, 1/I 를 뺀 알파벳
		String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
		String code = SecureTokens.randomFrom(alphabet, 8);
		assertThat(code).hasSize(8);
		for (char c : code.toCharArray()) {
			assertThat(alphabet).contains(String.valueOf(c));
		}
	}

	@Test
	void randomFromRejectsEmptyAlphabet() {
		assertThatThrownBy(() -> SecureTokens.randomFrom("", 8))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void sha256HexMatchesKnownVector() {
		// 표준 테스트 벡터 — 통합 과정에서 알고리즘이 바뀌지 않았음을 못박는다.
		// 기존에 저장된 토큰 해시와 호환되어야 하므로 값이 달라지면 안 된다.
		assertThat(SecureTokens.sha256Hex("abc"))
				.isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
		assertThat(SecureTokens.sha256Hex(""))
				.isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
	}

	@Test
	void sha256HexIsStable() {
		String token = SecureTokens.randomHex(32);
		assertThat(SecureTokens.sha256Hex(token)).isEqualTo(SecureTokens.sha256Hex(token));
		assertThat(SecureTokens.sha256Hex(token)).hasSize(64);
	}
}
