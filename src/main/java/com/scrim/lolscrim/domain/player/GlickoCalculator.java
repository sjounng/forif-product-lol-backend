package com.scrim.lolscrim.domain.player;

/**
 * 경기 후 점수 갱신 — Glicko-1. DESIGN.md §4.2.
 *
 * 5v5 한 판을 "나 vs 상대팀 평균"의 1v1 로 근사한다 (팀 단위로 사고하는 기존 UI/스키마와 일관).
 * rd 가 클수록 한 판의 영향이 커지는 게 스키마 의도이므로 고정 K값 Elo 가 아니라 Glicko-1 을 쓴다.
 */
public final class GlickoCalculator {

	private static final double Q = Math.log(10) / 400.0;
	private static final double PI_SQUARED = Math.PI * Math.PI;

	/**
	 * E 가 0/1 에 정확히 붙으면 d² 가 발산한다. 실력차가 극단적일 때만 걸리는 안전장치.
	 */
	private static final double E_EPSILON = 1e-9;

	/**
	 * RD 하한. DESIGN §4.2-A.
	 *
	 * Glicko 는 판이 쌓일수록 RD 가 단조 감소하는데, 바닥이 없으면 RD 가 0 으로 수렴해
	 * 한 판의 영향도 0 이 된다 — 실력이 변해도 점수가 따라가지 못하고 얼어붙는다.
	 * 그래서 표준 Glicko 관행대로 하한을 둔다.
	 */
	public static final int MIN_RD = 30;

	private GlickoCalculator() {
	}

	/**
	 * @param rawDelta      오프롤 감쇠(§4.3) 적용 <b>전</b> 점수 변화량. 감쇠 계수는 호출자가 곱한다.
	 * @param newRd         갱신된 RD. 승패와 무관하다 (기존 RD 와 d² 만으로 정해진다).
	 * @param expectedScore 기대 승률 E. 감사 기록(§4.2-B)에 남겨 "왜 이만큼 움직였나"를 설명한다.
	 */
	public record Outcome(double rawDelta, int newRd, double expectedScore) {
	}

	/**
	 * @param rating         내 현재 rating
	 * @param rd             내 현재 RD
	 * @param opponentRating 상대팀 평균 rating
	 * @param opponentRd     상대팀 평균 RD
	 * @param win            이겼으면 true (S = 1), 졌으면 false (S = 0)
	 */
	public static Outcome update(
			int rating,
			int rd,
			double opponentRating,
			double opponentRd,
			boolean win) {
		double g = g(opponentRd);
		double e = expectedScore(rating, opponentRating, g);
		double dSquared = 1.0 / (Q * Q * g * g * e * (1 - e));
		double rdSquared = (double) rd * rd;
		double inverseVariance = 1.0 / rdSquared + 1.0 / dSquared;

		double score = win ? 1.0 : 0.0;
		double rawDelta = (Q / inverseVariance) * g * (score - e);
		// floor 여야 한다. round 로 접으면 한 판당 감소폭이 0.5 미만이 되는 지점(상대 RD 200 기준
		// RD 55 근처)에서 감소가 통째로 지워져 RD 가 거기서 영구히 멈춘다 — 시드 RD 최소가 200이라
		// MIN_RD 는 도달조차 못 하는 죽은 상수가 된다. floor 는 매 판 최소 1씩 내려가므로 하한에 닿는다.
		int newRd = (int) Math.floor(Math.sqrt(1.0 / inverseVariance));
		return new Outcome(rawDelta, Math.max(MIN_RD, newRd), e);
	}

	/** g(RD) — 상대의 불확실성이 클수록 결과를 덜 신뢰한다. */
	static double g(double rd) {
		return 1.0 / Math.sqrt(1.0 + 3.0 * Q * Q * rd * rd / PI_SQUARED);
	}

	/** E — 내가 이길 기대 확률. */
	static double expectedScore(double rating, double opponentRating, double g) {
		double e = 1.0 / (1.0 + Math.pow(10, -g * (rating - opponentRating) / 400.0));
		return Math.min(Math.max(e, E_EPSILON), 1 - E_EPSILON);
	}
}
