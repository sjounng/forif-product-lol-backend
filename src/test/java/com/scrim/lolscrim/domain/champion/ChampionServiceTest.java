package com.scrim.lolscrim.domain.champion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

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
import com.scrim.lolscrim.domain.match.ScrimMatchRepository;
import com.scrim.lolscrim.domain.player.Lane;

@ExtendWith(MockitoExtension.class)
class ChampionServiceTest {

	@Mock
	private ChampionRepository championRepository;
	@Mock
	private MatchParticipantRepository matchParticipantRepository;
	@Mock
	private ScrimMatchRepository scrimMatchRepository;

	@Test
	void returnsOnlyEnabledChampionsOrderedByKoreanName() {
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
		ChampionService service = service();

		List<ChampionResponse> responses = service.getActiveChampions();

		assertThat(responses).singleElement().satisfies(response -> {
			assertThat(response.id()).isEqualTo(266);
			assertThat(response.riotId()).isEqualTo("Aatrox");
			assertThat(response.nameKo()).isEqualTo("아트록스");
			assertThat(response.imageUrl()).isEqualTo("https://ddragon.example/Aatrox.png");
		});
		verify(championRepository).findAllByEnabledTrueOrderByNameKoAsc();
	}

	@Test
	void globalAnalyticsIncludesAllCompletedMatchesAndExcludesJadeChampions() {
		Champion garen = champion(86, "Garen", "가렌");
		Champion jadeGaren = champion(60086, "Jade_Garen", "가렌");
		ChampionLaneAnalyticsProjection garenTop = projection(86, Lane.TOP, 4L, 3L, 11.5, 4L);
		ChampionLaneAnalyticsProjection jadeTop = org.mockito.Mockito.mock(ChampionLaneAnalyticsProjection.class);
		when(jadeTop.getChampionId()).thenReturn(60086);
		when(scrimMatchRepository.countByStatus(MatchStatus.COMPLETED)).thenReturn(7L);
		when(matchParticipantRepository.aggregateChampionAnalyticsByMatchStatus(MatchStatus.COMPLETED))
				.thenReturn(List.of(garenTop, jadeTop));
		when(championRepository.findAllById(List.of(86, 60086))).thenReturn(List.of(garen, jadeGaren));

		ChampionAnalyticsResponse response = service().getGlobalAnalytics();

		assertThat(response.totalMatches()).isEqualTo(7L);
		assertThat(response.rows()).singleElement().satisfies(row -> {
			assertThat(row.champion().riotId()).isEqualTo("Garen");
			assertThat(row.lane()).isEqualTo(Lane.TOP);
			assertThat(row.picks()).isEqualTo(4L);
			assertThat(row.wins()).isEqualTo(3L);
			assertThat(row.kdaSum()).isEqualTo(11.5);
			assertThat(row.kdaSamples()).isEqualTo(4L);
		});
	}

	private ChampionService service() {
		return new ChampionService(championRepository, matchParticipantRepository, scrimMatchRepository);
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
