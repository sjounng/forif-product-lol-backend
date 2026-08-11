package com.scrim.lolscrim.domain.champion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.scrim.lolscrim.domain.champion.ChampionSnapshot.ChampionData;

@ExtendWith(MockitoExtension.class)
class ChampionSyncWriterTest {

	@Mock
	private ChampionRepository championRepository;

	private ChampionSyncWriter writer;

	@BeforeEach
	void setUp() {
		writer = new ChampionSyncWriter(championRepository);
	}

	@Test
	void insertsNewChampion() {
		when(championRepository.countByEnabledTrue()).thenReturn(0L);
		when(championRepository.findAll()).thenReturn(List.of());

		ChampionSyncWriter.ChampionSyncResult result = writer.apply(snapshot(aatrox()));

		ArgumentCaptor<Champion> captor = ArgumentCaptor.forClass(Champion.class);
		verify(championRepository).save(captor.capture());
		assertThat(captor.getValue().getId()).isEqualTo(266);
		assertThat(captor.getValue().getNameKo()).isEqualTo("아트록스");
		assertThat(captor.getValue().isEnabled()).isTrue();
		assertThat(result.created()).isEqualTo(1);
	}

	@Test
	void updatesExistingChampion() {
		Champion existing = Champion.create(
				"16.13.1",
				new ChampionData(
						266,
						"Aatrox",
						"이전 이름",
						"Old name",
						List.of("Tank"),
						"https://old.example/Aatrox.png"));
		when(championRepository.countByEnabledTrue()).thenReturn(1L);
		when(championRepository.countByEnabledTrueAndDdragonVersion("16.14.1")).thenReturn(0L);
		when(championRepository.findAll()).thenReturn(List.of(existing));

		ChampionSyncWriter.ChampionSyncResult result = writer.apply(snapshot(aatrox()));

		assertThat(existing.getNameKo()).isEqualTo("아트록스");
		assertThat(existing.getNameEn()).isEqualTo("Aatrox");
		assertThat(existing.getTags()).containsExactly("Fighter");
		assertThat(existing.getDdragonVersion()).isEqualTo("16.14.1");
		assertThat(existing.isEnabled()).isTrue();
		assertThat(result.updated()).isEqualTo(1);
		verify(championRepository, never()).save(any());
	}

	@Test
	void softDisablesChampionMissingFromResponse() {
		Champion removed = Champion.create(
				"16.13.1",
				new ChampionData(
						103,
						"Ahri",
						"아리",
						"Ahri",
						List.of("Mage", "Assassin"),
						"https://old.example/Ahri.png"));
		when(championRepository.countByEnabledTrue()).thenReturn(1L);
		when(championRepository.countByEnabledTrueAndDdragonVersion("16.14.1")).thenReturn(0L);
		when(championRepository.findAll()).thenReturn(List.of(removed));

		ChampionSyncWriter.ChampionSyncResult result = writer.apply(snapshot(aatrox()));

		assertThat(removed.isEnabled()).isFalse();
		assertThat(result.disabled()).isEqualTo(1);
		verify(championRepository).save(any(Champion.class));
	}

	@Test
	void repeatedSynchronizationDoesNotCreateDuplicate() {
		when(championRepository.countByEnabledTrue()).thenReturn(0L, 1L);
		when(championRepository.countByEnabledTrueAndDdragonVersion("16.14.1")).thenReturn(1L);
		when(championRepository.findAll()).thenReturn(List.of());

		ChampionSyncWriter.ChampionSyncResult first = writer.apply(snapshot(aatrox()));
		ChampionSyncWriter.ChampionSyncResult second = writer.apply(snapshot(aatrox()));

		assertThat(first.created()).isEqualTo(1);
		assertThat(second.skipped()).isTrue();
		verify(championRepository, times(1)).save(any(Champion.class));
		verify(championRepository, times(1)).findAll();
	}

	private static ChampionSnapshot snapshot(ChampionData... champions) {
		return new ChampionSnapshot("16.14.1", List.of(champions));
	}

	private static ChampionData aatrox() {
		return new ChampionData(
				266,
				"Aatrox",
				"아트록스",
				"Aatrox",
				List.of("Fighter"),
				"https://ddragon.leagueoflegends.com/cdn/16.14.1/img/champion/Aatrox.png");
	}
}
