package com.scrim.lolscrim.domain.riot;

/**
 * 솔로/듀오 랭크 티어 -&gt; ladder_score(0~4000) 변환. DESIGN.md §4.1.
 *
 * ladder_score = tier_index*400 + division*100 + LP  (0~4000 클램프)
 * 마스터 이상은 division 없이 tier_index*400 + LP.
 */
public final class LadderScoreCalculator {

	private static final int POINTS_PER_TIER = 400;
	private static final int POINTS_PER_DIVISION = 100;
	private static final int MAX_LADDER_SCORE = 4000;

	private LadderScoreCalculator() {
	}

	/**
	 * @param tier     UNRANKED 는 호출하지 않는다 — 시드는 §4.1-B(DEFAULT)로 별도 처리한다.
	 * @param division 마스터 이상이면 무시한다 — Riot API가 마스터 이상에도 rank="I"를 얹어서 주는
	 *                 레거시 필드라, null이 아니어도 여기서 걸러낸다.
	 * @param leaguePoints LP.
	 */
	public static int calculate(Tier tier, RankDivision division, int leaguePoints) {
		if (tier == Tier.UNRANKED) {
			throw new IllegalArgumentException("UNRANKED 은 ladder_score 대상이 아닙니다.");
		}
		int tierIndex = tier.ordinal() - 1; // UNRANKED(0)을 제외한 인덱스
		boolean hasDivision = tier.ordinal() < Tier.MASTER.ordinal();
		int divisionScore = (hasDivision && division != null) ? divisionRank(division) * POINTS_PER_DIVISION : 0;
		int raw = tierIndex * POINTS_PER_TIER + divisionScore + leaguePoints;
		return Math.clamp(raw, 0, MAX_LADDER_SCORE);
	}

	// I 가 가장 높은 디비전이므로 3, IV 가 0
	private static int divisionRank(RankDivision division) {
		return 3 - division.ordinal();
	}
}
