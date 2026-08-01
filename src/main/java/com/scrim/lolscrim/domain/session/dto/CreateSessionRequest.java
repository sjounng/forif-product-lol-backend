package com.scrim.lolscrim.domain.session.dto;

import java.util.List;

import com.scrim.lolscrim.domain.session.FearlessMode;
import com.scrim.lolscrim.domain.session.MatchFormat;
import com.scrim.lolscrim.domain.session.TeamSide;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateSessionRequest(
		@Size(max = 100) String name,
		@NotNull MatchFormat matchFormat,
		@NotNull FearlessMode fearlessMode,
		@NotNull Boolean ratingEnabled,
		@NotNull TeamSide creatorSide,
		@NotNull Long opponentCaptainUserId,
		@NotNull @Size(min = 5, max = 5) List<@Valid SessionRosterMemberRequest> blueTeam,
		@NotNull @Size(min = 5, max = 5) List<@Valid SessionRosterMemberRequest> redTeam) {
}
