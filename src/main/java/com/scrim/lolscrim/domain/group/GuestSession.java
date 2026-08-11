package com.scrim.lolscrim.domain.group;

import java.time.LocalDateTime;

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
@Table(name = "guest_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GuestSession {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "room_id", nullable = false)
	private Long roomId;

	@Column(name = "token", nullable = false, length = 64)
	private String tokenHash;

	@Column(name = "nickname", length = 50)
	private String nickname;

	@Column(name = "is_banned", nullable = false)
	private boolean banned;

	@Column(name = "ip", length = 16)
	private byte[] ip;

	@Column(name = "last_seen_at", nullable = false)
	private LocalDateTime lastSeenAt;

	@Column(name = "expires_at")
	private LocalDateTime expiresAt;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	public static GuestSession create(
			Long roomId,
			String tokenHash,
			String nickname,
			byte[] ip,
			LocalDateTime now,
			LocalDateTime expiresAt) {
		GuestSession guest = new GuestSession();
		guest.roomId = roomId;
		guest.tokenHash = tokenHash;
		guest.nickname = nickname;
		guest.banned = false;
		guest.ip = ip;
		guest.lastSeenAt = now;
		guest.expiresAt = expiresAt;
		guest.createdAt = now;
		return guest;
	}

	public boolean isUsable(LocalDateTime now) {
		return !banned && (expiresAt == null || expiresAt.isAfter(now));
	}

	public void rename(String nickname, LocalDateTime now) {
		this.nickname = nickname;
		this.lastSeenAt = now;
	}

	public void eject(LocalDateTime now) {
		this.expiresAt = now;
		this.lastSeenAt = now;
	}
}
