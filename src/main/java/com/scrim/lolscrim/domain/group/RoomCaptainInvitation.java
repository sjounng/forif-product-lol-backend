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
@Table(name = "room_captain_invitations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoomCaptainInvitation {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "room_id", nullable = false)
	private Long roomId;

	@Column(name = "invitee_user_id", nullable = false)
	private Long inviteeUserId;

	@Column(name = "invited_by_user_id", nullable = false)
	private Long invitedByUserId;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false)
	private CaptainInvitationStatus status;

	@Column(name = "expires_at", nullable = false)
	private LocalDateTime expiresAt;

	@Column(name = "responded_at")
	private LocalDateTime respondedAt;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	public static RoomCaptainInvitation create(
			Long roomId,
			Long inviteeUserId,
			Long invitedByUserId,
			LocalDateTime now,
			LocalDateTime expiresAt) {
		RoomCaptainInvitation invitation = new RoomCaptainInvitation();
		invitation.roomId = roomId;
		invitation.inviteeUserId = inviteeUserId;
		invitation.invitedByUserId = invitedByUserId;
		invitation.status = CaptainInvitationStatus.PENDING;
		invitation.expiresAt = expiresAt;
		invitation.createdAt = now;
		invitation.updatedAt = now;
		return invitation;
	}

	public boolean isExpired(LocalDateTime now) {
		return !expiresAt.isAfter(now);
	}

	public void expire(LocalDateTime now) {
		status = CaptainInvitationStatus.EXPIRED;
		respondedAt = now;
		updatedAt = now;
	}

	public void accept(LocalDateTime now) {
		status = CaptainInvitationStatus.ACCEPTED;
		respondedAt = now;
		updatedAt = now;
	}

	public void reject(LocalDateTime now) {
		status = CaptainInvitationStatus.REJECTED;
		respondedAt = now;
		updatedAt = now;
	}

	public void cancel(LocalDateTime now) {
		status = CaptainInvitationStatus.CANCELLED;
		respondedAt = now;
		updatedAt = now;
	}
}

