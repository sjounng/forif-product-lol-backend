package com.scrim.lolscrim.domain.draft.realtime;

public record DraftRealtimeEvent(
		Long draftId,
		Integer seq,
		Integer version,
		String type,
		Object payload) {
}
