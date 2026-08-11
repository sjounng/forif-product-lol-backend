package com.scrim.lolscrim.domain.champion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.scrim.lolscrim.domain.champion.ChampionSnapshot.ChampionData;
import com.scrim.lolscrim.domain.champion.DataDragonChampionCatalog.ChampionPayload;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ChampionCatalogMerger {

	private final DataDragonProperties properties;

	public ChampionSnapshot merge(
			String version,
			DataDragonChampionCatalog koreanCatalog,
			DataDragonChampionCatalog englishCatalog) {
		validateCatalogs(version, koreanCatalog, englishCatalog);

		Map<Integer, ChampionPayload> koreanByKey = indexByChampionKey(koreanCatalog, "ko_KR");
		Map<Integer, ChampionPayload> englishByKey = indexByChampionKey(englishCatalog, "en_US");
		if (!koreanByKey.keySet().equals(englishByKey.keySet())) {
			throw new IllegalStateException("Data Dragon locale champion key sets do not match.");
		}

		List<ChampionData> champions = new ArrayList<>(koreanByKey.size());
		for (Integer championKey : koreanByKey.keySet()) {
			ChampionPayload ko = koreanByKey.get(championKey);
			ChampionPayload en = englishByKey.get(championKey);
			validatePair(championKey, ko, en);
			champions.add(new ChampionData(
					championKey,
					ko.id(),
					ko.name(),
					en.name(),
					ko.tags(),
					imageUrl(version, ko.image().full())));
		}
		champions.sort(Comparator.comparing(ChampionData::id));
		return new ChampionSnapshot(version, champions);
	}

	private void validateCatalogs(
			String version,
			DataDragonChampionCatalog koreanCatalog,
			DataDragonChampionCatalog englishCatalog) {
		if (version == null || version.isBlank()) {
			throw new IllegalStateException("Data Dragon version is empty.");
		}
		if (koreanCatalog == null || koreanCatalog.data() == null || koreanCatalog.data().isEmpty()) {
			throw new IllegalStateException("Data Dragon Korean champion catalog is empty.");
		}
		if (englishCatalog == null || englishCatalog.data() == null || englishCatalog.data().isEmpty()) {
			throw new IllegalStateException("Data Dragon English champion catalog is empty.");
		}
	}

	private static Map<Integer, ChampionPayload> indexByChampionKey(
			DataDragonChampionCatalog catalog,
			String locale) {
		Map<Integer, ChampionPayload> indexed = new HashMap<>();
		for (ChampionPayload champion : catalog.data().values()) {
			if (champion == null || champion.key() == null || champion.key().isBlank()) {
				throw new IllegalStateException("Data Dragon champion key is missing: locale=" + locale);
			}
			int championKey;
			try {
				championKey = Integer.parseInt(champion.key());
			} catch (NumberFormatException e) {
				throw new IllegalStateException(
						"Data Dragon champion key is invalid: locale=" + locale + ", key=" + champion.key(),
						e);
			}
			if (championKey < 0 || championKey > 65_535 || indexed.put(championKey, champion) != null) {
				throw new IllegalStateException(
						"Data Dragon champion key is out of range or duplicated: locale=" + locale
								+ ", key=" + championKey);
			}
		}
		return indexed;
	}

	private static void validatePair(Integer championKey, ChampionPayload ko, ChampionPayload en) {
		if (ko.id() == null || ko.id().isBlank() || !ko.id().equals(en.id())) {
			throw new IllegalStateException("Data Dragon locale champion IDs do not match: key=" + championKey);
		}
		if (ko.name() == null || ko.name().isBlank() || en.name() == null || en.name().isBlank()) {
			throw new IllegalStateException("Data Dragon champion name is missing: key=" + championKey);
		}
		if (ko.tags() == null || ko.image() == null || ko.image().full() == null || ko.image().full().isBlank()) {
			throw new IllegalStateException("Data Dragon champion metadata is missing: key=" + championKey);
		}
	}

	private String imageUrl(String version, String imageFile) {
		String baseUrl = properties.baseUrl().endsWith("/")
				? properties.baseUrl().substring(0, properties.baseUrl().length() - 1)
				: properties.baseUrl();
		return baseUrl + "/cdn/" + version + "/img/champion/" + imageFile;
	}
}
