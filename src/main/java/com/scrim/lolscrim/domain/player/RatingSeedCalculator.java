package com.scrim.lolscrim.domain.player;

/**
 * ladder_score -&gt; 초기 시드 rating/RD 변환. DESIGN.md §4.1-B.
 */
public final class RatingSeedCalculator {

	public static final int DEFAULT_RATING = 1500;
	private static final int SEED_OFFSET = 100;
	private static final int RD_RANKED_SEED = 200;
	private static final int RD_DEFAULT = 350;

	private RatingSeedCalculator() {
	}

	public record Seed(int rating, int rd) {
	}

	/** 솔/듀랭 조회에 성공한 경우. */
	public static Seed fromLadderScore(int ladderScore) {
		return new Seed(ladderScore + SEED_OFFSET, RD_RANKED_SEED);
	}

	/** 조회 실패·미조회·수동 등록인 경우. */
	public static Seed defaultSeed() {
		return new Seed(DEFAULT_RATING, RD_DEFAULT);
	}
}
