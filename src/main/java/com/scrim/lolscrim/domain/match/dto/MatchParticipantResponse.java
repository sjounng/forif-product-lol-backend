package com.scrim.lolscrim.domain.match.dto;

import com.scrim.lolscrim.domain.session.Lane;
import com.scrim.lolscrim.domain.session.TeamSide;

public record MatchParticipantResponse(
		Long playerId,
		String displayName,
		TeamSide side,
		Lane lane,
		ChampionSummary champion,
		Integer kills,
		Integer deaths,
		Integer assists) {

	public record ChampionSummary(
			Integer id,
			String riotId,
			String nameKo,
			String imageUrl) {
	}
}
