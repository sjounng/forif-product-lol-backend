package com.scrim.lolscrim.domain.riot;

/**
 * Riot 조회 결과. {@code status != OK} 이면 나머지 필드는 의미 없다.
 * {@code status == OK} 이지만 랭크 정보가 없으면(언랭) tier 가 UNRANKED, queueType 은 null.
 */
public record RiotSyncResult(
		RiotSyncStatus status,
		Long riotAccountId,
		QueueType queueType,
		Tier tier,
		RankDivision division,
		int leaguePoints,
		int ladderScore) {

	public static RiotSyncResult failed(RiotSyncStatus status) {
		return new RiotSyncResult(status, null, null, null, null, 0, 0);
	}

	public static RiotSyncResult unranked(Long riotAccountId) {
		return new RiotSyncResult(RiotSyncStatus.OK, riotAccountId, null, Tier.UNRANKED, null, 0, 0);
	}

	public static RiotSyncResult ranked(
			Long riotAccountId, QueueType queueType, Tier tier, RankDivision division,
			int leaguePoints, int ladderScore) {
		return new RiotSyncResult(RiotSyncStatus.OK, riotAccountId, queueType, tier, division, leaguePoints,
				ladderScore);
	}

	public boolean isRanked() {
		return status == RiotSyncStatus.OK && tier != null && tier != Tier.UNRANKED;
	}
}
