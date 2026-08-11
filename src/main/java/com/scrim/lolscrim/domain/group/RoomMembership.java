package com.scrim.lolscrim.domain.group;

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
@Table(name = "room_memberships")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoomMembership {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "room_id", nullable = false)
	private Long roomId;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Enumerated(EnumType.STRING)
	@Column(name = "role", nullable = false)
	private GroupRole role;

	@Column(name = "is_active", nullable = false)
	private boolean active;

	@Column(name = "joined_at", nullable = false)
	private LocalDateTime joinedAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	public static RoomMembership create(
			Long roomId,
			Long userId,
			GroupRole role,
			LocalDateTime now) {
		RoomMembership membership = new RoomMembership();
		membership.roomId = roomId;
		membership.userId = userId;
		membership.role = role;
		membership.active = true;
		membership.joinedAt = now;
		membership.updatedAt = now;
		return membership;
	}

	public void activate(GroupRole role, LocalDateTime now) {
		this.role = role;
		this.active = true;
		this.updatedAt = now;
	}

	public void changeRole(GroupRole role, LocalDateTime now) {
		this.role = role;
		this.updatedAt = now;
	}

	public void deactivate(LocalDateTime now) {
		this.active = false;
		this.updatedAt = now;
	}
}
