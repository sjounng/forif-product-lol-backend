package com.scrim.lolscrim.domain.riot;

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
@Table(name = "riot_accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RiotAccount {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "puuid", nullable = false, length = 78)
	private String puuid;

	@Enumerated(EnumType.STRING)
	@Column(name = "platform", nullable = false)
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

	@Column(name = "last_synced_at")
	private LocalDateTime lastSyncedAt;

	@Enumerated(EnumType.STRING)
	@Column(name = "sync_status", nullable = false)
	private RiotSyncStatus syncStatus;

	@Column(name = "created_at", insertable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", insertable = false, updatable = false)
	private LocalDateTime updatedAt;

	public static RiotAccount create(String puuid, RiotPlatform platform, String gameName, String tagLine) {
		RiotAccount account = new RiotAccount();
		account.puuid = puuid;
		account.platform = platform;
		account.gameName = gameName;
		account.tagLine = tagLine;
		account.syncStatus = RiotSyncStatus.OK;
		return account;
	}

	/** Riot 이 summoner-v4 에서 summonerId 를 더 이상 안 줘서 summonerId 필드는 항상 null로 남는다. */
	public void applySummoner(Integer profileIconId, Integer summonerLevel, LocalDateTime now) {
		this.profileIconId = profileIconId;
		this.summonerLevel = summonerLevel;
		this.syncStatus = RiotSyncStatus.OK;
		this.lastSyncedAt = now;
	}

	public void markSyncFailed(RiotSyncStatus status, LocalDateTime now) {
		this.syncStatus = status;
		this.lastSyncedAt = now;
	}
}
