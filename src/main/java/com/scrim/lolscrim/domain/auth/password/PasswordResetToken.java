package com.scrim.lolscrim.domain.auth.password;

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
@Table(name = "password_reset_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PasswordResetToken {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "token_hash", nullable = false, length = 64)
	private String tokenHash;

	@Column(name = "expires_at", nullable = false)
	private LocalDateTime expiresAt;

	@Column(name = "used_at")
	private LocalDateTime usedAt;

	@Column(name = "created_at", insertable = false, updatable = false)
	private LocalDateTime createdAt;

	public static PasswordResetToken create(Long userId, String tokenHash, LocalDateTime expiresAt) {
		PasswordResetToken token = new PasswordResetToken();
		token.userId = userId;
		token.tokenHash = tokenHash;
		token.expiresAt = expiresAt;
		return token;
	}

	public boolean isUsable(LocalDateTime now) {
		return usedAt == null && expiresAt.isAfter(now);
	}

	public void markUsed(LocalDateTime at) {
		usedAt = at;
	}
}
