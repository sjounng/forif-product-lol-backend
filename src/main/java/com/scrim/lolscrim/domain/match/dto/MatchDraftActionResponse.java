package com.scrim.lolscrim.domain.match.dto;

import com.scrim.lolscrim.domain.draft.DraftActionType;
import com.scrim.lolscrim.domain.match.dto.MatchParticipantResponse.ChampionSummary;
import com.scrim.lolscrim.domain.session.TeamSide;

public record MatchDraftActionResponse(
		int stepNo,
		TeamSide side,
		DraftActionType actionType,
		ChampionSummary champion,
		Long playerId,
		boolean auto) {
}
