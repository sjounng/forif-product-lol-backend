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

	/**
	 * 라인별 초기 RD. DESIGN.md §4.1-C.
	 *
	 * 전체 RD 를 5개 라인에 그대로 복사하면, 한 번도 안 가본 라인까지 "신뢰도 높음"으로 출발한다.
	 * §4.2 의 Glicko 는 RD 가 작을수록 한 판의 영향을 줄이므로, 그러면 <b>실측이 가장 필요한
	 * 라인일수록 점수가 가장 느리게 교정된다</b> — 방향이 반대로 걸린다.
	 * 그래서 lanePool(그 라인을 얼마나 뛰었나)이 낮을수록 RD 를 키운다.
	 *
	 * @param baseRd    전체 시드 RD (§4.1-B)
	 * @param lanePool  라인 숙련도 0~5 (§5.1-A)
	 */
	public static int laneRd(int baseRd, int lanePool) {
		int rd = switch (lanePool) {
			case 5, 4 -> baseRd;                       // 주/부라인 — 전체 시드만큼 믿는다
			case 3, 2 -> (baseRd + RD_DEFAULT) / 2;    // 가끔 간 라인 — 중간
			default -> RD_DEFAULT;                     // 0~1: 최근에 간 적 없음 — 모른다고 본다
		};
		return Math.min(rd, RD_DEFAULT);
	}
}
