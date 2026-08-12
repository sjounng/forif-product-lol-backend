package com.scrim.lolscrim.domain.riot;

/**
 * 솔로/듀오 랭크 티어 -&gt; ladder_score(0~4000) 변환. DESIGN.md §4.1.
 *
 * <p>다이아 이하: {@code tier_index*400 + division*100 + LP} (LP 0~99)
 *
 * <p>마스터 이상: {@code 2800 + 로그압축(LP)}. 티어 인덱스를 더하지 않는 이유는
 * Master/GM/Challenger 가 <b>하나의 연속된 LP 풀을 공유</b>하기 때문이다 — 챌린저는
 * 정의상 GM 보다 LP 가 높으므로 LP 만으로 이미 순서가 정해진다. 여기에 tier_index*400 을
 * 또 더하면 이중 계산이 되고, 실제로 챌린저는 LP 400 만 넘으면 전원 상한(4000)에 붙어
 * <b>서로 구분이 사라진다</b>(실측: KR 챌린저 LP 1901~2650 이 전부 4000).
 */
public final class LadderScoreCalculator {

	private static final int POINTS_PER_TIER = 400;
	private static final int POINTS_PER_DIVISION = 100;
	private static final int MAX_LADDER_SCORE = 4000;

	/** 다이아 I 99LP(2799) 바로 위. 마스터 0LP 의 시작점 */
	private static final int MASTER_BASE = 2800;
	/** MASTER_BASE + APEX_SPAN = MAX_LADDER_SCORE */
	private static final int APEX_SPAN = MAX_LADDER_SCORE - MASTER_BASE;
	/** 이 LP 에서 상한(4000)에 닿는다. KR 챌린저 1위가 대략 이 부근 */
	private static final int APEX_LP_CEILING = 3000;
	/** 로그 곡선의 완만함. 작을수록 저LP 구간이 빠르게 벌어진다 */
	private static final int APEX_LP_SCALE = 300;

	private LadderScoreCalculator() {
	}

	/**
	 * @param tier     UNRANKED 는 호출하지 않는다 — 시드는 §4.1-B(DEFAULT)로 별도 처리한다.
	 * @param division 마스터 이상이면 무시한다 — Riot API가 마스터 이상에도 rank="I"를 얹어서 주는
	 *                 레거시 필드라, null이 아니어도 여기서 걸러낸다.
	 * @param leaguePoints LP. 다이아 이하는 0~99, 마스터 이상은 상한이 없다.
	 */
	public static int calculate(Tier tier, RankDivision division, int leaguePoints) {
		if (tier == Tier.UNRANKED) {
			throw new IllegalArgumentException("UNRANKED 은 ladder_score 대상이 아닙니다.");
		}
		int lp = Math.max(0, leaguePoints);
		if (tier.ordinal() >= Tier.MASTER.ordinal()) {
			return apexScore(lp);
		}
		int tierIndex = tier.ordinal() - 1; // UNRANKED(0)을 제외한 인덱스
		int divisionScore = division != null ? divisionRank(division) * POINTS_PER_DIVISION : 0;
		return Math.clamp(tierIndex * POINTS_PER_TIER + divisionScore + lp, 0, MAX_LADDER_SCORE);
	}

	/**
	 * 마스터 이상 LP 를 [MASTER_BASE, MAX_LADDER_SCORE] 로 로그 압축한다.
	 *
	 * <p>선형으로 두면 LP 2000 인 사람이 LP 500 인 사람보다 4배 세다는 뜻이 되어 상위 구간을
	 * 과대평가한다. 실제로는 그 구간 전체가 상위 0.01% 안이라 실력 차가 LP 차만큼 벌어지지 않는다.
	 */
	private static int apexScore(int leaguePoints) {
		double ratio = Math.log10(1 + (double) leaguePoints / APEX_LP_SCALE)
				/ Math.log10(1 + (double) APEX_LP_CEILING / APEX_LP_SCALE);
		return MASTER_BASE + (int) Math.round(APEX_SPAN * Math.min(ratio, 1.0));
	}

	// I 가 가장 높은 디비전이므로 3, IV 가 0
	private static int divisionRank(RankDivision division) {
		return 3 - division.ordinal();
	}
}
