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
@Table(name = "draft_actions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DraftAction {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "draft_id", nullable = false)
	private Long draftId;

	@Column(name = "step_no", nullable = false)
	private Byte stepNo;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private TeamSide side;

	@Enumerated(EnumType.STRING)
	@Column(name = "action_type", nullable = false)
	private DraftActionType actionType;

	@Column(name = "champion_id", columnDefinition = "SMALLINT UNSIGNED")
	private Integer championId;

	@Column(name = "player_id")
	private Long playerId;

	@Column(name = "actor_user_id")
	private Long actorUserId;

	@Column(name = "is_auto", nullable = false)
	private boolean auto;

	@Column(name = "acted_at", nullable = false)
	private LocalDateTime actedAt;

	public static DraftAction lock(
			Long draftId,
			DraftRulesetStep step,
			Integer championId,
			Long playerId,
			Long actorUserId,
			boolean auto,
			LocalDateTime now) {
		DraftAction action = new DraftAction();
		action.draftId = draftId;
		action.stepNo = step.getStepNo();
		action.side = step.getSide();
		action.actionType = step.getActionType();
		action.championId = championId;
		action.playerId = playerId;
		action.actorUserId = actorUserId;
		action.auto = auto;
		action.actedAt = now;
		return action;
	}
}
