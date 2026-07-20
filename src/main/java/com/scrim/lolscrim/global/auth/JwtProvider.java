package com.scrim.lolscrim.global.auth;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.scrim.lolscrim.global.error.ApiException;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.Getter;

@Component
public class JwtProvider {

	private final SecretKey key;

	@Getter
	private final long accessTokenTtlSeconds;

	public JwtProvider(
			@Value("${app.jwt.secret}") String secret,
			@Value("${app.jwt.access-token-ttl-seconds}") long accessTokenTtlSeconds) {
		this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
		this.accessTokenTtlSeconds = accessTokenTtlSeconds;
	}

	public String createAccessToken(Long userId) {
		Date now = new Date();
		return Jwts.builder()
				.subject(String.valueOf(userId))
				.issuedAt(now)
				.expiration(new Date(now.getTime() + accessTokenTtlSeconds * 1000))
				.signWith(key)
				.compact();
	}

	public Long parseUserId(String token) {
		try {
			String subject = Jwts.parser()
					.verifyWith(key)
					.build()
					.parseSignedClaims(token)
					.getPayload()
					.getSubject();
			return Long.parseLong(subject);
		} catch (JwtException | NumberFormatException e) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다.");
		}
	}
}
