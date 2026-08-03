package com.scrim.lolscrim.domain.match.dto;

import com.scrim.lolscrim.domain.session.TeamSide;

import jakarta.validation.constraints.NotNull;

public record RequestMatchStartRequest(@NotNull TeamSide blueTeamSide) {
}
