package com.scrim.lolscrim.domain.champion;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChampionSyncOnStartupTest {

	@Mock
	private ChampionSyncService championSyncService;

	@Test
	void startupContinuesWhenDataDragonSynchronizationFails() {
		DataDragonProperties properties = new DataDragonProperties(
				"https://ddragon.leagueoflegends.com",
				"ko_KR",
				"en_US",
				true,
				5_000,
				10_000);
		when(championSyncService.synchronize()).thenThrow(new IllegalStateException("Data Dragon unavailable"));
		ChampionSyncOnStartup listener = new ChampionSyncOnStartup(properties, championSyncService);

		assertThatCode(listener::synchronizeAfterStartup).doesNotThrowAnyException();
		verify(championSyncService).synchronize();
	}
}
