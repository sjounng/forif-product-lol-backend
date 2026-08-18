package com.scrim.lolscrim.domain.champion;

import java.util.Comparator;
import java.util.Map;
import java.util.List;
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
import com.scrim.lolscrim.domain.match.ScrimMatchRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ChampionService {
	private static final String NON_STANDARD_PREFIX = "Jade_";

	private final ChampionRepository championRepository;
	private final MatchParticipantRepository matchParticipantRepository;
	private final ScrimMatchRepository scrimMatchRepository;

	@Transactional(readOnly = true)
	public List<ChampionResponse> getActiveChampions() {
		return championRepository.findAllByEnabledTrueOrderByNameKoAsc().stream()
				.filter(ChampionService::isStandardChampion)
				.map(ChampionResponse::from)
				.toList();
	}

	@Transactional(readOnly = true)
	public ChampionAnalyticsResponse getGlobalAnalytics() {
		long totalMatches = scrimMatchRepository.countByStatus(MatchStatus.COMPLETED);
		List<ChampionLaneAnalyticsProjection> projections =
				matchParticipantRepository.aggregateChampionAnalyticsByMatchStatus(MatchStatus.COMPLETED);
		Map<Integer, Champion> champions = championRepository.findAllById(
				projections.stream().map(ChampionLaneAnalyticsProjection::getChampionId).distinct().toList())
				.stream()
				.filter(ChampionService::isStandardChampion)
				.collect(Collectors.toMap(Champion::getId, Function.identity()));

		List<ChampionLaneStat> rows = projections.stream()
				.filter(projection -> champions.containsKey(projection.getChampionId()))
				.map(projection -> toAnalyticsRow(projection, champions.get(projection.getChampionId())))
				.sorted(Comparator
						.comparing((ChampionLaneStat row) -> row.champion().nameKo())
						.thenComparing(ChampionLaneStat::lane))
				.toList();
		return new ChampionAnalyticsResponse(totalMatches, rows);
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

	private static boolean isStandardChampion(Champion champion) {
		return !champion.getRiotId().startsWith(NON_STANDARD_PREFIX);
	}
}
