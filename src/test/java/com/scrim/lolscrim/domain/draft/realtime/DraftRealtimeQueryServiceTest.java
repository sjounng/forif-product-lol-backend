package com.scrim.lolscrim.domain.draft.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.scrim.lolscrim.domain.draft.DraftEvent;
import com.scrim.lolscrim.domain.draft.DraftEventRepository;
import com.scrim.lolscrim.domain.draft.DraftService;
import com.scrim.lolscrim.domain.draft.dto.DraftStateResponse;

class DraftRealtimeQueryServiceTest {

	private final DraftService draftService = mock(DraftService.class);
	private final DraftEventRepository eventRepository = mock(DraftEventRepository.class);
	private final DraftRealtimeQueryService service = new DraftRealtimeQueryService(
			draftService, eventRepository);

	@Test
	void firstConnectionReceivesFullSnapshot() {
		DraftStateResponse snapshot = snapshot(5, 3);
		when(draftService.getDraft(1L, 60L)).thenReturn(snapshot);

		List<DraftRealtimeEvent> events = service.eventsForConnection(1L, 60L, -1);

		assertThat(events).singleElement().satisfies(event -> {
			assertThat(event.type()).isEqualTo("SNAPSHOT");
			assertThat(event.seq()).isEqualTo(5);
			assertThat(event.payload()).isSameAs(snapshot);
		});
	}

	@Test
	void reconnectReplaysContinuousEventsAfterLastSequence() {
		DraftStateResponse snapshot = snapshot(4, 3);
		DraftEvent event3 = DraftEvent.create(
				60L, 3, 2, "HOVER_UPDATED", Map.of("championId", 266), LocalDateTime.now());
		DraftEvent event4 = DraftEvent.create(
				60L, 4, 3, "ACTION_LOCKED", Map.of("championId", 266), LocalDateTime.now());
		when(draftService.getDraft(1L, 60L)).thenReturn(snapshot);
		when(eventRepository.findAllByDraftIdAndSeqGreaterThanOrderBySeqAsc(60L, 2))
				.thenReturn(List.of(event3, event4));

		List<DraftRealtimeEvent> events = service.eventsForConnection(1L, 60L, 2);

		assertThat(events).extracting(DraftRealtimeEvent::seq).containsExactly(3, 4);
		assertThat(events).extracting(DraftRealtimeEvent::version).containsExactly(2, 3);
	}

	@Test
	void eventGapFallsBackToSnapshot() {
		DraftStateResponse snapshot = snapshot(5, 3);
		DraftEvent event5 = DraftEvent.create(
				60L, 5, 3, "ACTION_LOCKED", Map.of(), LocalDateTime.now());
		when(draftService.getDraft(1L, 60L)).thenReturn(snapshot);
		when(eventRepository.findAllByDraftIdAndSeqGreaterThanOrderBySeqAsc(60L, 2))
				.thenReturn(List.of(event5));

		List<DraftRealtimeEvent> events = service.eventsForConnection(1L, 60L, 2);

		assertThat(events).singleElement().extracting(DraftRealtimeEvent::type).isEqualTo("SNAPSHOT");
	}

	@Test
	void middleEventGapFallsBackToSnapshot() {
		DraftStateResponse snapshot = snapshot(5, 4);
		DraftEvent event3 = DraftEvent.create(
				60L, 3, 2, "HOVER_UPDATED", Map.of(), LocalDateTime.now());
		DraftEvent event5 = DraftEvent.create(
				60L, 5, 4, "ACTION_LOCKED", Map.of(), LocalDateTime.now());
		when(draftService.getDraft(1L, 60L)).thenReturn(snapshot);
		when(eventRepository.findAllByDraftIdAndSeqGreaterThanOrderBySeqAsc(60L, 2))
				.thenReturn(List.of(event3, event5));

		List<DraftRealtimeEvent> events = service.eventsForConnection(1L, 60L, 2);

		assertThat(events).singleElement().extracting(DraftRealtimeEvent::type).isEqualTo("SNAPSHOT");
	}

	private DraftStateResponse snapshot(int lastSeq, int version) {
		DraftStateResponse snapshot = mock(DraftStateResponse.class);
		when(snapshot.draftId()).thenReturn(60L);
		when(snapshot.lastEventSeq()).thenReturn(lastSeq);
		when(snapshot.version()).thenReturn(version);
		return snapshot;
	}
}
