package com.scrim.lolscrim.domain.user.dto;

import com.scrim.lolscrim.domain.auth.dto.UserResponse;
import com.scrim.lolscrim.domain.player.dto.RiotAccountResponse;
import com.scrim.lolscrim.domain.player.Lane;

public record UserProfileResponse(
		UserResponse user,
		RiotAccountResponse riotAccount,
		Lane primaryLane,
		Lane secondaryLane) {
}
