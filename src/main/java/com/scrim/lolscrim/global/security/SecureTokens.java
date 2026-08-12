package com.scrim.lolscrim.global.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * 난수 토큰 생성과 단방향 해시.
 *
 * <p>리프레시 토큰·비밀번호 재설정 토큰·게스트 세션 토큰·밴픽 좌석 토큰이 전부 같은 방식을 쓰는데
 * 클래스마다 {@code SecureRandom} 과 {@code sha256Hex} 를 따로 들고 있었다. 같은 코드가 여러 벌이면
 * 한쪽만 고쳐지는 사고가 나므로 여기로 모은다.
 *
 * <p><b>해시는 토큰 전용이다.</b> 비밀번호에는 쓰지 말 것 — SHA-256 은 빠른 해시라 무차별 대입에
 * 약하다. 비밀번호는 {@code PasswordEncoder}(bcrypt)를 쓴다. 여기서 SHA-256 이 괜찮은 이유는
 * 대상이 사람이 만든 문자열이 아니라 256비트 난수라서 사전 공격이 성립하지 않기 때문이다.
 */
public final class SecureTokens {

	/** {@code SecureRandom} 은 스레드 안전하다. 인스턴스를 여러 개 둘 이유가 없다. */
	private static final SecureRandom RANDOM = new SecureRandom();

	private SecureTokens() {
	}

	/**
	 * 난수 바이트를 16진 문자열로. 길이는 {@code byteLength * 2} 가 된다.
	 *
	 * @param byteLength 엔트로피 바이트 수. 저장 컬럼 길이와 맞출 것
	 *                   (예: {@code CHAR(64)} 이면 32바이트)
	 */
	public static String randomHex(int byteLength) {
		if (byteLength < 1) {
			throw new IllegalArgumentException("byteLength 는 1 이상이어야 합니다: " + byteLength);
		}
		byte[] bytes = new byte[byteLength];
		RANDOM.nextBytes(bytes);
		return HexFormat.of().formatHex(bytes);
	}

	/**
	 * 주어진 문자 집합에서 난수 문자열을 만든다. 사람이 눈으로 읽고 입력하는 코드용
	 * (예: 그룹 공개 코드 — 0/O, 1/I 를 뺀 알파벳을 넘긴다).
	 */
	public static String randomFrom(String alphabet, int length) {
		if (alphabet == null || alphabet.isEmpty()) {
			throw new IllegalArgumentException("alphabet 이 비어 있습니다.");
		}
		StringBuilder sb = new StringBuilder(length);
		for (int i = 0; i < length; i++) {
			sb.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
		}
		return sb.toString();
	}

	/** 토큰을 저장하기 전에 거는 단방향 해시. 평문 토큰은 DB 에 남기지 않는다. */
	public static String sha256Hex(String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			// SHA-256 은 모든 JVM 이 반드시 제공한다 (JLS 요구사항). 여기 오면 런타임이 깨진 것이다.
			throw new IllegalStateException("SHA-256 을 사용할 수 없습니다.", e);
		}
	}
}
