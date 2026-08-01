package com.scrim.lolscrim.domain.champion;

import org.springframework.stereotype.Service;

import com.scrim.lolscrim.domain.champion.ChampionSyncWriter.ChampionSyncResult;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ChampionSyncService {

	private final DataDragonClient dataDragonClient;
	private final DataDragonProperties properties;
	private final ChampionCatalogMerger championCatalogMerger;
	private final ChampionSyncWriter championSyncWriter;

	public synchronized ChampionSyncResult synchronize() {
		String version = dataDragonClient.fetchLatestVersion();
		DataDragonChampionCatalog korean = dataDragonClient.fetchChampionCatalog(
				version,
				properties.koreanLocale());
		DataDragonChampionCatalog english = dataDragonClient.fetchChampionCatalog(
				version,
				properties.englishLocale());
		ChampionSnapshot snapshot = championCatalogMerger.merge(version, korean, english);
		return championSyncWriter.apply(snapshot);
	}
}
