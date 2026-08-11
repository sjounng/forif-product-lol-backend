package com.scrim.lolscrim.domain.draft;

import java.time.LocalDateTime;

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
@Table(name = "draft_hovers")
@IdClass(DraftHoverId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DraftHover {

	@Id
	@Column(name = "draft_id", nullable = false)
	private Long draftId;

	@Id
	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private TeamSide side;

	@Column(name = "step_no", nullable = false)
	private Byte stepNo;

	@Column(name = "champion_id", columnDefinition = "SMALLINT UNSIGNED")
	private Integer championId;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	public static DraftHover create(
			Long draftId,
			TeamSide side,
			int stepNo,
			Integer championId,
			LocalDateTime now) {
		DraftHover hover = new DraftHover();
		hover.draftId = draftId;
		hover.side = side;
		hover.update(stepNo, championId, now);
		return hover;
	}

	public void update(int stepNo, Integer championId, LocalDateTime now) {
		this.stepNo = (byte) stepNo;
		this.championId = championId;
		this.updatedAt = now;
	}
}
