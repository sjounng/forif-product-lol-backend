package com.scrim.lolscrim.domain.champion;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.scrim.lolscrim.domain.champion.ChampionSyncWriter.ChampionSyncResult;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChampionSyncOnStartup {

	private final DataDragonProperties properties;
	private final ChampionSyncService championSyncService;

	@EventListener(ApplicationReadyEvent.class)
	public void synchronizeAfterStartup() {
		if (!properties.syncOnStartup()) {
			return;
		}
		try {
			ChampionSyncResult result = championSyncService.synchronize();
			log.info(
					"Data Dragon champion sync completed: version={}, created={}, updated={}, disabled={}, skipped={}",
					result.version(),
					result.created(),
					result.updated(),
					result.disabled(),
					result.skipped());
		} catch (Exception e) {
			log.error("Data Dragon champion sync failed. The application will continue with existing data.", e);
		}
	}
}
