package com.scrim.lolscrim.domain.player;

import java.time.LocalDateTime;

import com.scrim.lolscrim.domain.session.ParticipantType;

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
@Table(name = "players")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Player {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "room_id", nullable = false)
	private Long roomId;

	@Column(name = "riot_account_id")
	private Long riotAccountId;

	@Column(name = "member_user_id")
	private Long memberUserId;

	@Column(name = "guest_session_id")
	private Long guestSessionId;

	@Column(name = "display_name", nullable = false, length = 50)
	private String displayName;

	@Column(name = "memo", length = 255)
	private String memo;

	@Column(name = "is_active", nullable = false)
	private boolean active;

	@Column(name = "added_by_user_id")
	private Long addedByUserId;

	@Column(name = "created_at", insertable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", insertable = false, updatable = false)
	private LocalDateTime updatedAt;

	public static Player create(Long roomId, Long riotAccountId, String displayName, Long addedByUserId) {
		return fromRiotAccount(roomId, riotAccountId, displayName, addedByUserId, LocalDateTime.now());
	}

	public static Player fromMember(Long roomId, Long userId, String displayName, Long addedBy, LocalDateTime now) {
		Player player = base(roomId, displayName, addedBy, now);
		player.memberUserId = userId;
		return player;
	}

	public static Player fromGuest(Long roomId, Long guestId, String displayName, Long addedBy, LocalDateTime now) {
		Player player = base(roomId, displayName, addedBy, now);
		player.guestSessionId = guestId;
		return player;
	}

	public static Player fromRiotAccount(
			Long roomId, Long riotAccountId, String displayName, Long addedBy, LocalDateTime now) {
		Player player = base(roomId, displayName, addedBy, now);
		player.riotAccountId = riotAccountId;
		return player;
	}

	private static Player base(Long roomId, String displayName, Long addedBy, LocalDateTime now) {
		Player player = new Player();
		player.roomId = roomId;
		player.displayName = displayName;
		player.active = true;
		player.addedByUserId = addedBy;
		player.createdAt = now;
		player.updatedAt = now;
		return player;
	}

	public void refreshDisplayName(String displayName, LocalDateTime now) {
		this.displayName = displayName;
		this.active = true;
		this.updatedAt = now;
	}

	public void attachMember(Long memberUserId, String displayName, LocalDateTime now) {
		this.memberUserId = memberUserId;
		refreshDisplayName(displayName, now);
	}

	public void attachRiotAccount(Long riotAccountId, LocalDateTime now) {
		this.riotAccountId = riotAccountId;
		this.active = true;
		this.updatedAt = now;
	}

	public void detachRiotAccount(LocalDateTime now) {
		this.riotAccountId = null;
		this.updatedAt = now;
	}

	public void deactivate() {
		deactivate(LocalDateTime.now());
	}

	public void deactivate(LocalDateTime now) {
		this.active = false;
		this.updatedAt = now;
	}

	public ParticipantType getParticipantType() {
		if (memberUserId != null) {
			return ParticipantType.MEMBER;
		}
		return guestSessionId != null ? ParticipantType.GUEST : ParticipantType.PLAYER;
	}

	public Long getSourceId() {
		if (memberUserId != null) {
			return memberUserId;
		}
		return guestSessionId != null ? guestSessionId : id;
	}
}
