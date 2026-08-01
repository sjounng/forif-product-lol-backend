package com.scrim.lolscrim.domain.player;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import com.scrim.lolscrim.domain.session.Lane;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "riot_accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RiotAccount {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 78)
	private String puuid;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private RiotPlatform platform;

	@Column(name = "game_name", nullable = false, length = 32)
	private String gameName;

	@Column(name = "tag_line", nullable = false, length = 10)
	private String tagLine;

	@Column(name = "summoner_id", length = 64)
	private String summonerId;

	@Column(name = "profile_icon_id")
	private Integer profileIconId;

	@Column(name = "summoner_level")
	private Integer summonerLevel;

	@Enumerated(EnumType.STRING)
	@Column(name = "primary_lane")
	private Lane primaryLane;

	@Enumerated(EnumType.STRING)
	@Column(name = "secondary_lane")
	private Lane secondaryLane;

	@Column(name = "last_synced_at")
	private LocalDateTime lastSyncedAt;

	@Enumerated(EnumType.STRING)
	@Column(name = "sync_status", nullable = false)
	private RiotSyncStatus syncStatus;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	public static RiotAccount create(
			String puuid,
			String gameName,
			String tagLine,
			LocalDateTime now) {
		RiotAccount account = new RiotAccount();
		account.puuid = puuid;
		account.platform = RiotPlatform.KR;
		account.refresh(gameName, tagLine, now);
		account.createdAt = now;
		return account;
	}

	public void refresh(String gameName, String tagLine, LocalDateTime now) {
		this.gameName = gameName;
		this.tagLine = tagLine;
		this.lastSyncedAt = now;
		this.syncStatus = RiotSyncStatus.OK;
		this.updatedAt = now;
	}

	public void refreshProfile(
			String gameName,
			String tagLine,
			String summonerId,
			Integer profileIconId,
			Integer summonerLevel,
			Lane primaryLane,
			Lane secondaryLane,
			LocalDateTime now) {
		refresh(gameName, tagLine, now);
		this.summonerId = summonerId;
		this.profileIconId = profileIconId;
		this.summonerLevel = summonerLevel;
		this.primaryLane = primaryLane;
		this.secondaryLane = secondaryLane;
	}
}
