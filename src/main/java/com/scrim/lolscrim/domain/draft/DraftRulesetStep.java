package com.scrim.lolscrim.domain.draft;

import com.scrim.lolscrim.domain.session.TeamSide;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "draft_ruleset_steps")
@IdClass(DraftRulesetStepId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DraftRulesetStep {

	@Id
	@Column(name = "ruleset_id", nullable = false, length = 32)
	private String rulesetId;

	@Id
	@Column(name = "step_no", nullable = false)
	private Byte stepNo;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private TeamSide side;

	@Enumerated(EnumType.STRING)
	@Column(name = "action_type", nullable = false)
	private DraftActionType actionType;

	@Column(nullable = false)
	private Byte phase;
}
