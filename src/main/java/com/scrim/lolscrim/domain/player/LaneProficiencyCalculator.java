package com.scrim.lolscrim.domain.player;

import java.util.EnumMap;
import java.util.Map;

/**
 * 최근 솔랭 라인 분포 -&gt; lanePool 숙련도(0~5) 추천. DESIGN.md §5.1-A.
 *
 * 주의: 여기서 나오는 값은 "선호"가 아니라 "최근에 실제로 간 라인"의 추정치다.
 * Riot API 에 선호 라인이라는 개념 자체가 없어서, 이건 관리자가 화면에서 고치기 전의
 * 초기 추천값으로만 쓴다.
 *
 * 0(배정 금지)은 자동으로 주지 않는다 — 최근에 안 갔다는 이유로 그 라인을 아예 막아버리면
 * 인원이 빠듯할 때 팀 구성이 실패한다. 0 은 관리자가 명시적으로 지정할 때만 쓴다.
 */
public final class LaneProficiencyCalculator {

	private static final int MIN_AUTO_PROFICIENCY = 1;

	private LaneProficiencyCalculator() {
	}

	/**
	 * @param laneGames 라인별 플레이 판수. 분석된 판이 하나도 없으면 전 라인 {@value #MIN_AUTO_PROFICIENCY}.
	 */
	public static Map<Lane, Integer> recommend(Map<Lane, Integer> laneGames) {
		int total = laneGames.values().stream().mapToInt(Integer::intValue).sum();

		Map<Lane, Integer> result = new EnumMap<>(Lane.class);
		for (Lane lane : Lane.values()) {
			int games = laneGames.getOrDefault(lane, 0);
			result.put(lane, total == 0 ? MIN_AUTO_PROFICIENCY : fromShare((double) games / total));
		}
		return result;
	}

	private static int fromShare(double share) {
		if (share >= 0.40) {
			return 5; // 주라인
		}
		if (share >= 0.20) {
			return 4;
		}
		if (share >= 0.10) {
			return 3;
		}
		if (share > 0.0) {
			return 2;
		}
		return MIN_AUTO_PROFICIENCY; // 최근에 간 적 없음 — 막지는 않고 크게 깎는다
	}
}
