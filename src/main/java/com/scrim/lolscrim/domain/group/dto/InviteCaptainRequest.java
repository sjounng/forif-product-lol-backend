package com.scrim.lolscrim.domain.group.dto;

import jakarta.validation.constraints.NotNull;

public record InviteCaptainRequest(@NotNull Long userId) {
}

