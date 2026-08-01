package com.scrim.lolscrim.domain.session.dto;

import com.scrim.lolscrim.domain.session.Lane;
import com.scrim.lolscrim.domain.session.ParticipantType;

public record SessionMemberResponse(
		Long playerId,
		ParticipantType participantType,
		Long participantId,
		String displayName,
		Lane lane,
		Lane primaryLane,
		Lane secondaryLane) {
}
