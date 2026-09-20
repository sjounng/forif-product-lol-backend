package com.scrim.lolscrim.domain.champion;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.scrim.lolscrim.domain.champion.dto.ChampionAnalyticsResponse;
import com.scrim.lolscrim.domain.champion.dto.ChampionAnalyticsResponse.ChampionLaneStat;
import com.scrim.lolscrim.domain.champion.dto.ChampionAnalyticsResponse.ChampionSummary;
import com.scrim.lolscrim.domain.champion.dto.ChampionResponse;
import com.scrim.lolscrim.domain.match.ChampionLaneAnalyticsProjection;
import com.scrim.lolscrim.domain.match.MatchParticipantRepository;
import com.scrim.lolscrim.domain.match.MatchStatus;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ChampionService {
	private final ChampionRepository championRepository;
	private final MatchParticipantRepository matchParticipantRepository;
	private final ChampionAnalyticsCache championAnalyticsCache;

	@Transactional(readOnly = true)
	public List<ChampionResponse> getActiveChampions() {
		return championRepository.findAllByEnabledTrueOrderByNameKoAsc().stream()
				.map(ChampionResponse::from)
				.toList();
	}

	@Transactional(readOnly = true)
	public synchronized ChampionAnalyticsResponse getGlobalAnalytics() {
		return championAnalyticsCache.get().orElseGet(this::loadGlobalAnalytics);
	}

	private ChampionAnalyticsResponse loadGlobalAnalytics() {
		long totalMatches = matchParticipantRepository
				.countMatchesWithChampionByMatchStatus(MatchStatus.COMPLETED);
		List<ChampionLaneAnalyticsProjection> projections =
				matchParticipantRepository.aggregateChampionAnalyticsByMatchStatus(MatchStatus.COMPLETED);
		Map<Integer, Champion> champions = championRepository.findAllById(
				projections.stream().map(ChampionLaneAnalyticsProjection::getChampionId).distinct().toList())
				.stream()
				.collect(Collectors.toMap(Champion::getId, Function.identity()));

		List<ChampionLaneStat> rows = projections.stream()
				.filter(projection -> champions.containsKey(projection.getChampionId()))
				.map(projection -> toAnalyticsRow(projection, champions.get(projection.getChampionId())))
				.sorted(Comparator
						.comparing((ChampionLaneStat row) -> row.champion().nameKo())
						.thenComparing(ChampionLaneStat::lane))
				.toList();
		ChampionAnalyticsResponse response = new ChampionAnalyticsResponse(totalMatches, rows);
		championAnalyticsCache.put(response);
		return response;
	}

	private static ChampionLaneStat toAnalyticsRow(
			ChampionLaneAnalyticsProjection projection,
			Champion champion) {
		return new ChampionLaneStat(
				ChampionSummary.from(champion),
				projection.getLane(),
				projection.getPicks(),
				projection.getWins(),
				projection.getKdaSum(),
				projection.getKdaSamples());
	}
}
