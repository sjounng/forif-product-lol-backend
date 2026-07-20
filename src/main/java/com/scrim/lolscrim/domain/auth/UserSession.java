package com.scrim.lolscrim.domain.auth;

import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserSession {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "refresh_token_hash", nullable = false, length = 64)
	private String refreshTokenHash;

	@Column(name = "user_agent", length = 255)
	private String userAgent;

	@Column(name = "ip", length = 16)
	private byte[] ip;

	@Column(name = "expires_at", nullable = false)
	private LocalDateTime expiresAt;

	@Column(name = "revoked_at")
	private LocalDateTime revokedAt;

	@Column(name = "created_at", insertable = false, updatable = false)
	private LocalDateTime createdAt;

	public static UserSession create(Long userId, String refreshTokenHash, String userAgent, byte[] ip,
			LocalDateTime expiresAt) {
		UserSession session = new UserSession();
		session.userId = userId;
		session.refreshTokenHash = refreshTokenHash;
		session.userAgent = userAgent;
		session.ip = ip;
		session.expiresAt = expiresAt;
		return session;
	}

	public boolean isUsable(LocalDateTime now) {
		return revokedAt == null && expiresAt.isAfter(now);
	}

	public void revoke(LocalDateTime at) {
		this.revokedAt = at;
	}
}
