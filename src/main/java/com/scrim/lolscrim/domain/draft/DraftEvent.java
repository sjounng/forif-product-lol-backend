package com.scrim.lolscrim.domain.draft;

import java.time.LocalDateTime;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "draft_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DraftEvent {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "draft_id", nullable = false)
	private Long draftId;

	@Column(nullable = false)
	private Integer seq;

	@Column(nullable = false)
	private Integer version;

	@Column(name = "event_type", nullable = false, length = 32)
	private String eventType;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = "JSON")
	private Map<String, Object> payload;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	public static DraftEvent create(
			Long draftId,
			Integer seq,
			Integer version,
			String eventType,
			Map<String, Object> payload,
			LocalDateTime now) {
		DraftEvent event = new DraftEvent();
		event.draftId = draftId;
		event.seq = seq;
		event.version = version;
		event.eventType = eventType;
		event.payload = Map.copyOf(payload);
		event.createdAt = now;
		return event;
	}
}
