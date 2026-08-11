package com.scrim.lolscrim.domain.champion;

import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class DataDragonClient {

	private final RestClient dataDragonRestClient;

	public String fetchLatestVersion() {
		String[] versions = dataDragonRestClient.get()
				.uri("/api/versions.json")
				.retrieve()
				.body(String[].class);
		if (versions == null || versions.length == 0 || versions[0] == null || versions[0].isBlank()) {
			throw new IllegalStateException("Data Dragon latest version response is empty.");
		}
		return versions[0];
	}

	public DataDragonChampionCatalog fetchChampionCatalog(String version, String locale) {
		DataDragonChampionCatalog catalog = dataDragonRestClient.get()
				.uri("/cdn/{version}/data/{locale}/champion.json", version, locale)
				.retrieve()
				.body(DataDragonChampionCatalog.class);
		if (catalog == null || catalog.data() == null || catalog.data().isEmpty()) {
			throw new IllegalStateException(
					"Data Dragon champion response is empty: version=" + version + ", locale=" + locale);
		}
		if (catalog.data().values().stream().anyMatch(java.util.Objects::isNull)) {
			throw new IllegalStateException(
					"Data Dragon champion response contains null data: version=" + version + ", locale=" + locale);
		}
		return new DataDragonChampionCatalog(Map.copyOf(catalog.data()));
	}
}
