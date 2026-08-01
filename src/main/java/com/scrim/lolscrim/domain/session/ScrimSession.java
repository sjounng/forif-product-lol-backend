package com.scrim.lolscrim.domain.session;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "scrim_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScrimSession {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "room_id", nullable = false)
	private Long roomId;

	@Column(name = "created_by_user_id")
	private Long createdByUserId;

	@Column(name = "name", length = 100)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(name = "match_format", nullable = false)
	private MatchFormat matchFormat;

	@Enumerated(EnumType.STRING)
	@Column(name = "fearless_mode", nullable = false)
	private FearlessMode fearlessMode;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false)
	private SessionStatus status;

	@Column(name = "rating_enabled", nullable = false)
	private boolean ratingEnabled;

	@Column(name = "rejection_reason", length = 500)
	private String rejectionReason;

	@Column(name = "proposed_at")
	private LocalDateTime proposedAt;

	@Column(name = "confirmed_at")
	private LocalDateTime confirmedAt;

	@Column(name = "game_count", nullable = false)
	private Byte gameCount;

	@Column(name = "started_at")
	private LocalDateTime startedAt;

	@Column(name = "ended_at")
	private LocalDateTime endedAt;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	public static ScrimSession propose(
			Long roomId,
			Long createdByUserId,
			String name,
			MatchFormat matchFormat,
			FearlessMode fearlessMode,
			boolean ratingEnabled,
			LocalDateTime now) {
		ScrimSession session = new ScrimSession();
		session.roomId = roomId;
		session.createdByUserId = createdByUserId;
		session.name = name == null || name.isBlank() ? null : name.trim();
		session.matchFormat = matchFormat;
		session.fearlessMode = fearlessMode;
		session.status = SessionStatus.PROPOSED;
		session.ratingEnabled = ratingEnabled;
		session.proposedAt = now;
		session.gameCount = 0;
		session.createdAt = now;
		session.updatedAt = now;
		return session;
	}

	public void confirm(LocalDateTime now) {
		status = SessionStatus.CONFIRMED;
		confirmedAt = now;
		updatedAt = now;
	}

	public void reject(String reason, LocalDateTime now) {
		status = SessionStatus.CANCELLED;
		rejectionReason = reason == null || reason.isBlank() ? null : reason.trim();
		endedAt = now;
		updatedAt = now;
	}

	public void cancel(LocalDateTime now) {
		status = SessionStatus.CANCELLED;
		endedAt = now;
		updatedAt = now;
	}

	public void startMatchFlow(LocalDateTime now) {
		if (status == SessionStatus.CONFIRMED) {
			status = SessionStatus.IN_PROGRESS;
			startedAt = now;
		}
		updatedAt = now;
	}

	public void recordCompletedMatch(boolean finish, LocalDateTime now) {
		gameCount = (byte) (gameCount.intValue() + 1);
		if (finish) {
			status = SessionStatus.FINISHED;
			endedAt = now;
		}
		updatedAt = now;
	}

	public void finish(LocalDateTime now) {
		status = SessionStatus.FINISHED;
		endedAt = now;
		updatedAt = now;
	}
}
