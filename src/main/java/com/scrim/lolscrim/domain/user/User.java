package com.scrim.lolscrim.domain.user;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "email", nullable = false, length = 255)
	private String email;

	@Column(name = "password_hash", length = 255)
	private String passwordHash;

	@Column(name = "display_name", nullable = false, length = 50)
	private String displayName;

	@Column(name = "avatar_url", length = 255)
	private String avatarUrl;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, columnDefinition = "enum('ACTIVE','SUSPENDED','DELETED')")
	private UserStatus status;

	@Column(name = "email_verified_at")
	private LocalDateTime emailVerifiedAt;

	@Column(name = "last_login_at")
	private LocalDateTime lastLoginAt;

	@Column(name = "created_at", insertable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", insertable = false, updatable = false)
	private LocalDateTime updatedAt;

	public static User create(String email, String passwordHash, String displayName) {
		User user = new User();
		user.email = email;
		user.passwordHash = passwordHash;
		user.displayName = displayName;
		user.status = UserStatus.ACTIVE;
		return user;
	}

	public void markLoggedIn(LocalDateTime at) {
		this.lastLoginAt = at;
	}

	public void changePassword(String newPasswordHash) {
		this.passwordHash = newPasswordHash;
	}
}
