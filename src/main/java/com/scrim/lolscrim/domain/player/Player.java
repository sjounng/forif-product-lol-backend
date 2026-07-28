package com.scrim.lolscrim.domain.player;

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
		Player player = new Player();
		player.roomId = roomId;
		player.riotAccountId = riotAccountId;
		player.displayName = displayName;
		player.addedByUserId = addedByUserId;
		player.active = true;
		return player;
	}

	public void deactivate() {
		this.active = false;
	}
}
