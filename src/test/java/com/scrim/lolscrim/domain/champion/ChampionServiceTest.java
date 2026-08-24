package com.scrim.lolscrim.domain.champion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.scrim.lolscrim.domain.champion.ChampionSnapshot.ChampionData;
import com.scrim.lolscrim.domain.champion.dto.ChampionAnalyticsResponse;
import com.scrim.lolscrim.domain.champion.dto.ChampionResponse;
import com.scrim.lolscrim.domain.match.ChampionLaneAnalyticsProjection;
import com.scrim.lolscrim.domain.match.MatchParticipantRepository;
import com.scrim.lolscrim.domain.match.MatchStatus;
import com.scrim.lolscrim.domain.player.Lane;

@ExtendWith(MockitoExtension.class)
class ChampionServiceTest {

	@Mock
	private ChampionRepository championRepository;
	@Mock
	private MatchParticipantRepository matchParticipantRepository;
	@Mock
	private ChampionAnalyticsCache championAnalyticsCache;

	@Test
	void returnsEnabledChampionsOrderedByKoreanName() {
		Champion aatrox = Champion.create(
				"16.14.1",
				new ChampionData(
						266,
						"Aatrox",
						"아트록스",
						"Aatrox",
						List.of("Fighter"),
						"https://ddragon.example/Aatrox.png"));
		when(championRepository.findAllByEnabledTrueOrderByNameKoAsc()).thenReturn(List.of(aatrox));

		List<ChampionResponse> responses = service().getActiveChampions();

		assertThat(responses).singleElement().satisfies(response -> {
			assertThat(response.id()).isEqualTo(266);
			assertThat(response.riotId()).isEqualTo("Aatrox");
			assertThat(response.nameKo()).isEqualTo("아트록스");
			assertThat(response.imageUrl()).isEqualTo("https://ddragon.example/Aatrox.png");
		});
		verify(championRepository).findAllByEnabledTrueOrderByNameKoAsc();
	}

	@Test
	void globalAnalyticsCountsChampionRecordedMatchesAndMapsRows() {
		Champion garen = champion(86, "Garen", "가렌");
		Champion ahri = champion(103, "Ahri", "아리");
		ChampionLaneAnalyticsProjection garenTop = projection(86, Lane.TOP, 4L, 3L, 11.5, 4L);
		ChampionLaneAnalyticsProjection ahriMid = projection(103, Lane.MID, 2L, 1L, 0.0, 0L);
		when(championAnalyticsCache.get()).thenReturn(Optional.empty());
		when(matchParticipantRepository.countMatchesWithChampionByMatchStatus(MatchStatus.COMPLETED))
				.thenReturn(6L);
		when(matchParticipantRepository.aggregateChampionAnalyticsByMatchStatus(MatchStatus.COMPLETED))
				.thenReturn(List.of(garenTop, ahriMid));
		when(championRepository.findAllById(List.of(86, 103))).thenReturn(List.of(garen, ahri));

		ChampionAnalyticsResponse response = service().getGlobalAnalytics();

		assertThat(response.totalMatches()).isEqualTo(6L);
		assertThat(response.rows()).hasSize(2);
		assertThat(response.rows().get(0).champion().riotId()).isEqualTo("Garen");
		assertThat(response.rows().get(0).kdaSum()).isEqualTo(11.5);
		assertThat(response.rows().get(1).champion().riotId()).isEqualTo("Ahri");
		assertThat(response.rows().get(1).kdaSamples()).isZero();
		verify(championAnalyticsCache).put(response);
	}

	@Test
	void globalAnalyticsReturnsCachedResponseWithoutQueryingDatabase() {
		ChampionAnalyticsResponse cached = new ChampionAnalyticsResponse(12L, List.of());
		when(championAnalyticsCache.get()).thenReturn(Optional.of(cached));

		ChampionAnalyticsResponse response = service().getGlobalAnalytics();

		assertThat(response).isSameAs(cached);
		verifyNoInteractions(championRepository, matchParticipantRepository);
	}

	@Test
	void globalAnalyticsSupportsEmptyProjections() {
		when(championAnalyticsCache.get()).thenReturn(Optional.empty());
		when(matchParticipantRepository.countMatchesWithChampionByMatchStatus(MatchStatus.COMPLETED))
				.thenReturn(0L);
		when(matchParticipantRepository.aggregateChampionAnalyticsByMatchStatus(MatchStatus.COMPLETED))
				.thenReturn(List.of());
		when(championRepository.findAllById(List.of())).thenReturn(List.of());

		ChampionAnalyticsResponse response = service().getGlobalAnalytics();

		assertThat(response.totalMatches()).isZero();
		assertThat(response.rows()).isEmpty();
		verify(championAnalyticsCache).put(response);
	}

	@Test
	void globalAnalyticsDropsRowsWithoutChampionMetadata() {
		ChampionLaneAnalyticsProjection missingChampion =
				org.mockito.Mockito.mock(ChampionLaneAnalyticsProjection.class);
		when(missingChampion.getChampionId()).thenReturn(999);
		when(championAnalyticsCache.get()).thenReturn(Optional.empty());
		when(matchParticipantRepository.countMatchesWithChampionByMatchStatus(MatchStatus.COMPLETED))
				.thenReturn(1L);
		when(matchParticipantRepository.aggregateChampionAnalyticsByMatchStatus(MatchStatus.COMPLETED))
				.thenReturn(List.of(missingChampion));
		when(championRepository.findAllById(List.of(999))).thenReturn(List.of());

		ChampionAnalyticsResponse response = service().getGlobalAnalytics();

		assertThat(response.totalMatches()).isEqualTo(1L);
		assertThat(response.rows()).isEmpty();
	}

	private ChampionService service() {
		return new ChampionService(championRepository, matchParticipantRepository, championAnalyticsCache);
	}

	private Champion champion(int id, String riotId, String nameKo) {
		return Champion.create(
				"16.15.1",
				new ChampionData(
						id,
						riotId,
						nameKo,
						riotId,
						List.of("Fighter"),
						"https://ddragon.example/" + riotId + ".png"));
	}

	private ChampionLaneAnalyticsProjection projection(
			int championId,
			Lane lane,
			long picks,
			long wins,
			double kdaSum,
			long kdaSamples) {
		ChampionLaneAnalyticsProjection projection = org.mockito.Mockito.mock(ChampionLaneAnalyticsProjection.class);
		when(projection.getChampionId()).thenReturn(championId);
		when(projection.getLane()).thenReturn(lane);
		when(projection.getPicks()).thenReturn(picks);
		when(projection.getWins()).thenReturn(wins);
		when(projection.getKdaSum()).thenReturn(kdaSum);
		when(projection.getKdaSamples()).thenReturn(kdaSamples);
		return projection;
	}
}
