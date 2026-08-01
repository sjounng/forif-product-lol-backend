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
@Table(name = "rooms")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Room {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "owner_user_id", nullable = false)
	private Long ownerUserId;

	@Column(name = "opponent_captain_user_id")
	private Long opponentCaptainUserId;

	@Column(name = "name", nullable = false, length = 100)
	private String name;

	@Column(name = "description", length = 500)
	private String description;

	@Column(name = "public_code", nullable = false, length = 8)
	private String publicCode;

	@Column(name = "entry_code_hash", length = 255)
	private String entryCodeHash;

	@Column(name = "entry_code_hint", length = 20)
	private String entryCodeHint;

	@Column(name = "entry_code_rotated_at")
	private LocalDateTime entryCodeRotatedAt;

	@Column(name = "guest_can_draft", nullable = false)
	private boolean guestCanDraft;

	@Column(name = "guest_admission_enabled", nullable = false)
	private boolean guestAdmissionEnabled;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false)
	private RoomStatus status;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	public static Room create(
			Long ownerUserId,
			String name,
			String description,
			String publicCode,
			String entryCodeHash,
			String entryCodeHint,
			boolean guestAdmissionEnabled,
			LocalDateTime now) {
		Room room = new Room();
		room.ownerUserId = ownerUserId;
		room.name = name;
		room.description = description;
		room.publicCode = publicCode;
		room.entryCodeHash = entryCodeHash;
		room.entryCodeHint = entryCodeHint;
		room.guestCanDraft = false;
		room.guestAdmissionEnabled = guestAdmissionEnabled;
		room.status = RoomStatus.ACTIVE;
		room.createdAt = now;
		room.updatedAt = now;
		return room;
	}

	public void assignOpponentCaptain(Long userId, LocalDateTime now) {
		opponentCaptainUserId = userId;
		updatedAt = now;
	}

	public void update(
			String name,
			String description,
			Boolean guestAdmissionEnabled,
			String entryCodeHash,
			String entryCodeHint,
			boolean updateEntryCode,
			LocalDateTime now) {
		if (name != null) {
			this.name = name;
		}
		if (description != null) {
			this.description = description.isBlank() ? null : description;
		}
		if (guestAdmissionEnabled != null) {
			this.guestAdmissionEnabled = guestAdmissionEnabled;
		}
		if (updateEntryCode) {
			this.entryCodeHash = entryCodeHash;
			this.entryCodeHint = entryCodeHint;
			this.entryCodeRotatedAt = now;
		}
		this.updatedAt = now;
	}

	public void rotatePublicCode(String publicCode, LocalDateTime now) {
		this.publicCode = publicCode;
		this.entryCodeRotatedAt = now;
		this.updatedAt = now;
	}

	public void archive(LocalDateTime now) {
		this.status = RoomStatus.ARCHIVED;
		this.updatedAt = now;
	}
}
