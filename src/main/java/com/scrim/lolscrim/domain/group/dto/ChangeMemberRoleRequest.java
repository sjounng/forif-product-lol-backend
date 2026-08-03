package com.scrim.lolscrim.domain.group.dto;

import com.scrim.lolscrim.domain.group.GroupRole;

import jakarta.validation.constraints.NotNull;

public record ChangeMemberRoleRequest(@NotNull GroupRole role) {
}

