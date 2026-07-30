package com.scrim.lolscrim.domain.riot;

import java.util.EnumMap;
import java.util.Map;

import com.scrim.lolscrim.domain.player.Lane;

/**
 * 최근 솔랭 라인 분포 조회 결과.
 *
 * 레이트리밋에 걸려 중간에 끊겨도 그때까지 모은 분포를 그대로 돌려준다 —
 * 부분 데이터라도 전 라인 기본값보다는 쓸모 있고, 플레이어 등록 자체를 실패시키지 않는다.
 * 그래서 {@code status} 가 OK 가 아니어도 {@code laneGames} 는 비어있지 않을 수 있다.
 */
public record LaneHistoryResult(RiotSyncStatus status, Map<Lane, Integer> laneGames, int analyzedMatches) {

	public static LaneHistoryResult of(RiotSyncStatus status, Map<Lane, Integer> laneGames, int analyzedMatches) {
		return new LaneHistoryResult(status, new EnumMap<>(laneGames), analyzedMatches);
	}

	public static LaneHistoryResult empty(RiotSyncStatus status) {
		return new LaneHistoryResult(status, new EnumMap<>(Lane.class), 0);
	}
}
