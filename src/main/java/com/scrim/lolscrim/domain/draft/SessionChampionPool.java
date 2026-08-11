package com.scrim.lolscrim.domain.draft;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "session_champion_pool")
@IdClass(SessionChampionPoolId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SessionChampionPool {

	@Id
	@Column(name = "scrim_session_id", nullable = false)
	private Long sessionId;

	@Id
	@Column(name = "champion_id", nullable = false, columnDefinition = "SMALLINT UNSIGNED")
	private Integer championId;

	@Id
	@Column(nullable = false, length = 4)
	private String side;

	@Column(nullable = false, length = 4)
	private String source;

	@Column(name = "used_in_match_id")
	private Long usedInMatchId;

	public static SessionChampionPool create(
			Long sessionId,
			Integer championId,
			String source,
			Long usedInMatchId) {
		SessionChampionPool pool = new SessionChampionPool();
		pool.sessionId = sessionId;
		pool.championId = championId;
		pool.side = "ANY";
		pool.source = source;
		pool.usedInMatchId = usedInMatchId;
		return pool;
	}
}
