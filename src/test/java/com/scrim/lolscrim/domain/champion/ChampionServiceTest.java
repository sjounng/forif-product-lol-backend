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
import com.scrim.lolscrim.domain.champion.dto.ChampionResponse;

@ExtendWith(MockitoExtension.class)
class ChampionServiceTest {

	@Mock
	private ChampionRepository championRepository;

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
		ChampionService service = new ChampionService(championRepository);

		List<ChampionResponse> responses = service.getActiveChampions();

		assertThat(responses).singleElement().satisfies(response -> {
			assertThat(response.id()).isEqualTo(266);
			assertThat(response.riotId()).isEqualTo("Aatrox");
			assertThat(response.nameKo()).isEqualTo("아트록스");
			assertThat(response.imageUrl()).isEqualTo("https://ddragon.example/Aatrox.png");
		});
		verify(championRepository).findAllByEnabledTrueOrderByNameKoAsc();
	}
}
