package com.scrim.lolscrim.domain.room;

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

	@Column(name = "name", nullable = false, length = 100)
	private String name;

	@Column(name = "description", length = 500)
	private String description;

	@Column(name = "public_code", nullable = false, length = 8)
	private String publicCode;

	@Column(name = "entry_code_hash", nullable = false)
	private String entryCodeHash;

	@Column(name = "entry_code_hint", length = 20)
	private String entryCodeHint;

	@Column(name = "entry_code_rotated_at")
	private LocalDateTime entryCodeRotatedAt;

	@Column(name = "guest_can_draft", nullable = false)
	private boolean guestCanDraft;

	/** tinyint unsigned */
	@Column(name = "team_size", nullable = false)
	private byte teamSize;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false)
	private RoomStatus status;

	@Column(name = "created_at", insertable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", insertable = false, updatable = false)
	private LocalDateTime updatedAt;

	public static Room create(
			Long ownerUserId, String name, String description, String publicCode,
			String entryCodeHash, String entryCodeHint) {
		Room room = new Room();
		room.ownerUserId = ownerUserId;
		room.name = name;
		room.description = description;
		room.publicCode = publicCode;
		room.entryCodeHash = entryCodeHash;
		room.entryCodeHint = entryCodeHint;
		room.entryCodeRotatedAt = LocalDateTime.now();
		room.guestCanDraft = true;
		room.teamSize = 5;
		room.status = RoomStatus.ACTIVE;
		return room;
	}
}
