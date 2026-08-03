package com.scrim.lolscrim.domain.champion;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChampionSyncServiceTest {

	@Mock
	private DataDragonClient dataDragonClient;

	@Mock
	private ChampionCatalogMerger championCatalogMerger;

	@Mock
	private ChampionSyncWriter championSyncWriter;

	private ChampionSyncService championSyncService;

	@BeforeEach
	void setUp() {
		DataDragonProperties properties = new DataDragonProperties(
				"https://ddragon.leagueoflegends.com",
				"ko_KR",
				"en_US",
				true,
				5_000,
				10_000);
		championSyncService = new ChampionSyncService(
				dataDragonClient,
				properties,
				championCatalogMerger,
				championSyncWriter);
	}

	@Test
	void doesNotModifyDatabaseWhenEnglishCatalogRequestFails() {
		DataDragonChampionCatalog korean = new DataDragonChampionCatalog(java.util.Map.of(
				"Aatrox",
				new DataDragonChampionCatalog.ChampionPayload(
						"Aatrox",
						"266",
						"아트록스",
						java.util.List.of("Fighter"),
						new DataDragonChampionCatalog.ImagePayload("Aatrox.png"))));
		when(dataDragonClient.fetchLatestVersion()).thenReturn("16.14.1");
		when(dataDragonClient.fetchChampionCatalog("16.14.1", "ko_KR")).thenReturn(korean);
		when(dataDragonClient.fetchChampionCatalog("16.14.1", "en_US"))
				.thenThrow(new IllegalStateException("Data Dragon unavailable"));

		assertThatThrownBy(() -> championSyncService.synchronize())
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("Data Dragon unavailable");

		verify(championCatalogMerger, never()).merge(
				org.mockito.ArgumentMatchers.any(),
				org.mockito.ArgumentMatchers.any(),
				org.mockito.ArgumentMatchers.any());
		verify(championSyncWriter, never()).apply(org.mockito.ArgumentMatchers.any());
	}
}
