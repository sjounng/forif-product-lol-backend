package com.scrim.lolscrim.domain.draft;

import java.io.Serializable;

import com.scrim.lolscrim.domain.session.TeamSide;

public record DraftHoverId(Long draftId, TeamSide side) implements Serializable {
}
