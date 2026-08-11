package com.scrim.lolscrim.domain.draft.realtime;

import java.util.List;
import java.util.stream.IntStream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.scrim.lolscrim.domain.draft.DraftEvent;
import com.scrim.lolscrim.domain.draft.DraftEventRepository;
import com.scrim.lolscrim.domain.draft.DraftService;
import com.scrim.lolscrim.domain.draft.dto.DraftStateResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DraftRealtimeQueryService {

	private final DraftService draftService;
	private final DraftEventRepository eventRepository;

	@Transactional(readOnly = true)
	public List<DraftRealtimeEvent> eventsForConnection(
			Long viewerUserId,
			Long draftId,
			Integer lastSeq) {
		DraftStateResponse snapshot = draftService.getDraft(viewerUserId, draftId);
		if (lastSeq == null || lastSeq < 0 || lastSeq > snapshot.lastEventSeq()) {
			return List.of(snapshotEvent(snapshot));
		}
		if (lastSeq.equals(snapshot.lastEventSeq())) {
			return List.of();
		}

		List<DraftEvent> events = eventRepository
				.findAllByDraftIdAndSeqGreaterThanOrderBySeqAsc(draftId, lastSeq);
		boolean continuous = events.size() == snapshot.lastEventSeq() - lastSeq
				&& IntStream.range(0, events.size())
						.allMatch(index -> events.get(index).getSeq() == lastSeq + index + 1);
		if (!continuous) {
			return List.of(snapshotEvent(snapshot));
		}
		return events.stream()
				.map(event -> new DraftRealtimeEvent(
						event.getDraftId(),
						event.getSeq(),
						event.getVersion(),
						event.getEventType(),
						event.getPayload()))
				.toList();
	}

	private DraftRealtimeEvent snapshotEvent(DraftStateResponse snapshot) {
		return new DraftRealtimeEvent(
				snapshot.draftId(),
				snapshot.lastEventSeq(),
				snapshot.version(),
				"SNAPSHOT",
				snapshot);
	}
}
