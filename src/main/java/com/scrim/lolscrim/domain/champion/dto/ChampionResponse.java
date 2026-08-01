package com.scrim.lolscrim.domain.champion.dto;

import java.util.List;

import com.scrim.lolscrim.domain.champion.Champion;

public record ChampionResponse(
		Integer id,
		String riotId,
		String nameKo,
		String nameEn,
		List<String> tags,
		String imageUrl,
		String ddragonVersion) {

	public static ChampionResponse from(Champion champion) {
		return new ChampionResponse(
				champion.getId(),
				champion.getRiotId(),
				champion.getNameKo(),
				champion.getNameEn(),
				champion.getTags() == null ? List.of() : List.copyOf(champion.getTags()),
				champion.getImageUrl(),
				champion.getDdragonVersion());
	}
}
