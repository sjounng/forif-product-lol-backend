package com.scrim.lolscrim.domain.session.dto;

import com.scrim.lolscrim.domain.player.Lane;
import com.scrim.lolscrim.domain.session.ParticipantType;

import jakarta.validation.constraints.NotNull;

public record SessionRosterMemberRequest(
		@NotNull ParticipantType participantType,
		@NotNull Long participantId,
		@NotNull Lane lane) {
}
