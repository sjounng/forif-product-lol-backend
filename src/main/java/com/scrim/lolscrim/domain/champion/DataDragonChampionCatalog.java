package com.scrim.lolscrim.domain.champion;

import java.util.List;
import java.util.Map;

public record DataDragonChampionCatalog(Map<String, ChampionPayload> data) {

	public record ChampionPayload(
			String id,
			String key,
			String name,
			List<String> tags,
			ImagePayload image) {
	}

	public record ImagePayload(String full) {
	}
}
