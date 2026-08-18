package com.scrim.lolscrim.domain.champion.dto;

import java.util.List;

import com.scrim.lolscrim.domain.champion.Champion;
import com.scrim.lolscrim.domain.player.Lane;

public record ChampionAnalyticsResponse(
		long totalMatches,
		List<ChampionLaneStat> rows) {

	public record ChampionLaneStat(
			ChampionSummary champion,
			Lane lane,
			long picks,
			long wins,
			double kdaSum,
			long kdaSamples) {
	}

	public record ChampionSummary(
			Integer id,
			String riotId,
			String nameKo,
			String imageUrl) {

		public static ChampionSummary from(Champion champion) {
			return new ChampionSummary(
					champion.getId(),
					champion.getRiotId(),
					champion.getNameKo(),
					champion.getImageUrl());
		}
	}
}
