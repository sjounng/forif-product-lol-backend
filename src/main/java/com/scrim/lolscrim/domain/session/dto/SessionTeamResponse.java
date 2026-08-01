package com.scrim.lolscrim.domain.session.dto;

import java.util.List;

import com.scrim.lolscrim.domain.group.dto.GroupUserResponse;
import com.scrim.lolscrim.domain.session.TeamSide;

public record SessionTeamResponse(
		TeamSide side,
		String teamName,
		GroupUserResponse captain,
		List<SessionMemberResponse> members) {
}
