package com.scrim.lolscrim.domain.draft;

import java.time.LocalDateTime;

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
@Table(name = "draft_assignments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DraftAssignment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "draft_id", nullable = false)
	private Long draftId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private TeamSide side;

	@Column(name = "player_id", nullable = false)
	private Long playerId;

	@Column(name = "champion_id", nullable = false, columnDefinition = "SMALLINT UNSIGNED")
	private Integer championId;

	@Column(name = "assigned_by_user_id")
	private Long assignedByUserId;

	@Column(name = "is_auto", nullable = false)
	private boolean auto;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	public static DraftAssignment create(
			Long draftId,
			TeamSide side,
			Long playerId,
			Integer championId,
			Long actorUserId,
			boolean auto,
			LocalDateTime now) {
		DraftAssignment assignment = new DraftAssignment();
		assignment.draftId = draftId;
		assignment.side = side;
		assignment.playerId = playerId;
		assignment.reassign(championId, actorUserId, auto, now);
		assignment.createdAt = now;
		return assignment;
	}

	public void reassign(Integer championId, Long actorUserId, boolean auto, LocalDateTime now) {
		this.championId = championId;
		this.assignedByUserId = actorUserId;
		this.auto = auto;
		this.updatedAt = now;
	}
}
