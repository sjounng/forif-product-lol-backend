package com.scrim.lolscrim.domain.match;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;

import com.scrim.lolscrim.domain.session.TeamSide;

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
@Table(name = "drafts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Draft {

	private static final SecureRandom SECURE_RANDOM = new SecureRandom();

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "match_id", nullable = false)
	private Long matchId;

	@Column(name = "scrim_session_id", nullable = false)
	private Long sessionId;

	@Column(name = "ruleset_id", nullable = false, length = 32)
	private String rulesetId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private DraftStatus status;

	@Column(name = "spectator_token", nullable = false, length = 32)
	private String spectatorToken;

	@Column(name = "blue_ready", nullable = false)
	private boolean blueReady;

	@Column(name = "red_ready", nullable = false)
	private boolean redReady;

	@Column(name = "blue_assignment_confirmed", nullable = false)
	private boolean blueAssignmentConfirmed;

	@Column(name = "red_assignment_confirmed", nullable = false)
	private boolean redAssignmentConfirmed;

	@Column(name = "current_step", nullable = false)
	private Byte currentStep;

	@Column(name = "timer_sec", nullable = false)
	private Short timerSec;

	@Column(name = "turn_deadline_at")
	private LocalDateTime turnDeadlineAt;

	@Column(name = "assignment_deadline_at")
	private LocalDateTime assignmentDeadlineAt;

	@Column(name = "blue_reserve_ms", nullable = false)
	private Integer blueReserveMs;

	@Column(name = "red_reserve_ms", nullable = false)
	private Integer redReserveMs;

	@Column(nullable = false)
	private Integer version;

	@Column(name = "last_event_seq", nullable = false)
	private Integer lastEventSeq;

	@Column(name = "started_at")
	private LocalDateTime startedAt;

	@Column(name = "completed_at")
	private LocalDateTime completedAt;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	public static Draft create(Long matchId, Long sessionId, LocalDateTime now) {
		Draft draft = new Draft();
		draft.matchId = matchId;
		draft.sessionId = sessionId;
		draft.rulesetId = "TOURNAMENT_STANDARD";
		draft.status = DraftStatus.WAITING;
		draft.spectatorToken = token();
		draft.currentStep = 0;
		draft.timerSec = 30;
		draft.blueReserveMs = 30_000;
		draft.redReserveMs = 30_000;
		draft.version = 0;
		draft.lastEventSeq = 0;
		draft.createdAt = now;
		return draft;
	}

	public boolean isReady(TeamSide side) {
		return side == TeamSide.BLUE ? blueReady : redReady;
	}

	public void ready(TeamSide side, LocalDateTime now) {
		if (side == TeamSide.BLUE) {
			blueReady = true;
		} else {
			redReady = true;
		}
		status = blueReady && redReady ? DraftStatus.IN_PROGRESS : DraftStatus.READY;
		if (status == DraftStatus.IN_PROGRESS) {
			currentStep = 1;
			startedAt = now;
			turnDeadlineAt = now.plusSeconds(timerSec);
		}
		version++;
	}

	public void lockCurrentStep(boolean lastStep, LocalDateTime now) {
		advanceAfterLock(lastStep, now);
	}

	public void lockCurrentStep(boolean lastStep, TeamSide actingSide, LocalDateTime now) {
		consumeReserve(actingSide, now);
		advanceAfterLock(lastStep, now);
	}

	private void advanceAfterLock(boolean lastStep, LocalDateTime now) {
		if (lastStep) {
			status = DraftStatus.ASSIGNING;
			assignmentDeadlineAt = now.plusSeconds(90);
			turnDeadlineAt = null;
		} else {
			currentStep = (byte) (currentStep.intValue() + 1);
			turnDeadlineAt = now.plusSeconds(timerSec);
		}
		version++;
	}

	public boolean isTurnExpired(TeamSide actingSide, LocalDateTime now) {
		if (turnDeadlineAt == null || now.isBefore(turnDeadlineAt)) {
			return false;
		}
		return Duration.between(turnDeadlineAt, now).toMillis() >= storedReserve(actingSide);
	}

	public int reserveRemaining(TeamSide side, TeamSide activeSide, LocalDateTime now) {
		int stored = storedReserve(side);
		if (side != activeSide || turnDeadlineAt == null || now.isBefore(turnDeadlineAt)) {
			return stored;
		}
		long spent = Duration.between(turnDeadlineAt, now).toMillis();
		return (int) Math.max(0, stored - spent);
	}

	private void consumeReserve(TeamSide side, LocalDateTime now) {
		if (turnDeadlineAt == null || now.isBefore(turnDeadlineAt)) {
			return;
		}
		int remaining = reserveRemaining(side, side, now);
		if (side == TeamSide.BLUE) {
			blueReserveMs = remaining;
		} else {
			redReserveMs = remaining;
		}
	}

	private int storedReserve(TeamSide side) {
		return side == TeamSide.BLUE ? blueReserveMs : redReserveMs;
	}

	public boolean isAssignmentConfirmed(TeamSide side) {
		return side == TeamSide.BLUE ? blueAssignmentConfirmed : redAssignmentConfirmed;
	}

	public void assignmentChanged() {
		version++;
	}

	public void confirmAssignment(TeamSide side) {
		if (side == TeamSide.BLUE) {
			blueAssignmentConfirmed = true;
		} else {
			redAssignmentConfirmed = true;
		}
		version++;
	}

	public boolean assignmentsConfirmed() {
		return blueAssignmentConfirmed && redAssignmentConfirmed;
	}

	public void complete(LocalDateTime now) {
		status = DraftStatus.COMPLETED;
		assignmentDeadlineAt = null;
		completedAt = now;
	}

	public int nextEventSeq() {
		lastEventSeq++;
		return lastEventSeq;
	}

	private static String token() {
		byte[] bytes = new byte[16];
		SECURE_RANDOM.nextBytes(bytes);
		return HexFormat.of().formatHex(bytes);
	}
}
