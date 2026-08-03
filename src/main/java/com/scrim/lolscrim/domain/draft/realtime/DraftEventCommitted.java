package com.scrim.lolscrim.domain.draft.realtime;

import java.util.Map;

public record DraftEventCommitted(
		Long draftId,
		Integer seq,
		Integer version,
		String type,
		Map<String, Object> payload) {

	public DraftRealtimeEvent toRealtimeEvent() {
		return new DraftRealtimeEvent(draftId, seq, version, type, payload);
	}
}
