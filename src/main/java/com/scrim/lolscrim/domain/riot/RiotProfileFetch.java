package com.scrim.lolscrim.domain.riot;

/**
 * Riot 에서 막 받아온 프로필 — 아직 DB 에 저장되지 않은 상태다.
 *
 * HTTP 조회(트랜잭션 밖)와 저장(트랜잭션 안)을 분리하기 위한 중간 표현이다.
 * {@code status != OK} 면 puuid 외 나머지 필드는 의미 없다.
 * {@code tier == UNRANKED} 면 랭크 기록이 없는 것이다 (조회 자체는 성공).
 */
public record RiotProfileFetch(
		RiotSyncStatus status,
		String puuid,
		String gameName,
		String tagLine,
		Integer profileIconId,
		Integer summonerLevel,
		QueueType queueType,
		Tier tier,
		RankDivision division,
		int leaguePoints,
		int wins,
		int losses,
		int ladderScore) {

	public static RiotProfileFetch failed(RiotSyncStatus status, String puuid, String gameName, String tagLine) {
		return new RiotProfileFetch(status, puuid, gameName, tagLine, null, null, null, null, null, 0, 0, 0, 0);
	}

	public boolean isOk() {
		return status == RiotSyncStatus.OK;
	}

	public boolean isRanked() {
		return isOk() && tier != null && tier != Tier.UNRANKED;
	}
}
