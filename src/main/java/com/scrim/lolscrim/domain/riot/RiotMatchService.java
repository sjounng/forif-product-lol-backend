package com.scrim.lolscrim.domain.riot;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.scrim.lolscrim.domain.player.Lane;
import com.scrim.lolscrim.domain.riot.dto.RiotMatchDto;

import lombok.RequiredArgsConstructor;

/**
 * match-v5 로 최근 솔랭 N판의 실제 플레이 라인을 집계한다.
 *
 * 비용: 1(매치 id 목록) + N(매치 상세) 회 호출. N=20 이면 21회다.
 * 개발 키는 20req/s 뿐 아니라 100req/2min 제한도 있어서, 플레이어를 연달아 등록하면
 * 금방 429 가 난다. 그래서 429 를 만나면 예외를 던지지 않고 그 시점까지 모은 분포를 반환한다.
 */
@Service
@RequiredArgsConstructor
public class RiotMatchService {

	private static final Logger log = LoggerFactory.getLogger(RiotMatchService.class);

	private final RiotApiClient riotApiClient;

	@Value("${app.riot.lane-analysis-match-count}")
	private int laneAnalysisMatchCount;

	/** 최근 솔랭 {@code app.riot.lane-analysis-match-count} 판의 라인 분포. */
	public LaneHistoryResult fetchRecentLaneHistory(String puuid) {
		RiotLookupResult<List<String>> idsResult = riotApiClient.lookupRecentMatchIds(
				puuid, RiotApiClient.QUEUE_RANKED_SOLO, laneAnalysisMatchCount);
		if (!idsResult.isOk()) {
			return LaneHistoryResult.empty(idsResult.status());
		}

		List<String> matchIds = idsResult.value();
		Map<Lane, Integer> laneGames = new EnumMap<>(Lane.class);
		int analyzed = 0;

		for (String matchId : matchIds) {
			RiotLookupResult<RiotMatchDto> matchResult = riotApiClient.lookupMatch(matchId);
			if (!matchResult.isOk()) {
				// 레이트리밋·일시 오류면 여기서 멈추고 지금까지 모은 것만 쓴다
				log.warn("매치 상세 조회 중단 (matchId={}, status={}). {}판까지 집계됨",
						matchId, matchResult.status(), analyzed);
				return LaneHistoryResult.of(matchResult.status(), laneGames, analyzed);
			}
			Lane lane = extractLane(matchResult.value(), puuid);
			if (lane != null) {
				laneGames.merge(lane, 1, Integer::sum);
				analyzed++;
			}
		}
		return LaneHistoryResult.of(RiotSyncStatus.OK, laneGames, analyzed);
	}

	/** 내 participant 를 찾아 teamPosition 을 Lane 으로 변환한다. 리메이크 등은 null. */
	private static Lane extractLane(RiotMatchDto match, String puuid) {
		if (match == null || match.info() == null || match.info().participants() == null) {
			return null;
		}
		return match.info().participants().stream()
				.filter(p -> puuid.equals(p.puuid()))
				.findFirst()
				.map(p -> Lane.fromRiotTeamPosition(p.teamPosition()))
				.orElse(null);
	}
}
