package com.scrim.lolscrim.domain.champion;

import java.util.List;

public record ChampionSnapshot(String version, List<ChampionData> champions) {

	public ChampionSnapshot {
		champions = List.copyOf(champions);
	}

	public record ChampionData(
			Integer id,
			String riotId,
			String nameKo,
			String nameEn,
			List<String> tags,
			String imageUrl) {

		public ChampionData {
			tags = List.copyOf(tags);
		}
	}
}
