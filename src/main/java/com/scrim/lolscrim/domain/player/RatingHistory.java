package com.scrim.lolscrim.domain.player;

import java.math.BigDecimal;
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

/**
 * 점수가 움직인 이유의 감사 기록.
 *
 * <p>스키마 주석 그대로 "수식 v2 전환 시 전체 재계산의 근거이자 <b>'왜 이만큼 올랐냐'의 답</b>"이다.
 * {@code player_ratings} 는 현재값만 들고 파괴적으로 갱신되므로, 이 테이블이 없으면
 * {@code MatchStatus.VOIDED}(결과 무효화) 시 점수를 되돌릴 수 없다.
 *
 * <p>{@code formula_version} 은 나중에 수식을 바꿨을 때 "어떤 수식으로 계산된 행인지"를 남긴다.
 */
@Entity
@Table(name = "rating_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RatingHistory {

	/** 현재 수식 세대. §4.2 Glicko-1 을 바꾸면 올린다. */
	public static final String FORMULA_V1 = "v1";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "player_id", nullable = false)
	private Long playerId;

	@Column(name = "room_id", nullable = false)
	private Long roomId;

	@Column(name = "match_id")
	private Long matchId;

	@Enumerated(EnumType.STRING)
	@Column(name = "scope", nullable = false)
	private RatingScope scope;

	@Enumerated(EnumType.STRING)
	@Column(name = "lane")
	private Lane lane;

	@Enumerated(EnumType.STRING)
	@Column(name = "reason", nullable = false)
	private RatingChangeReason reason;

	@Column(name = "rating_before", nullable = false)
	private int ratingBefore;

	@Column(name = "rating_after", nullable = false)
	private int ratingAfter;

	@Column(name = "delta", nullable = false)
	private int delta;

	/** smallint unsigned. PlayerRating.rd 와 타입을 맞춘다 (ddl-auto: validate 가 잡는다). */
	@Column(name = "rd_before")
	private Short rdBefore;

	@Column(name = "rd_after")
	private Short rdAfter;

	/** 기대 승률 E. 나중에 "왜 이만큼 움직였나"를 설명하는 핵심 값이다. */
	@Column(name = "expected_score")
	private BigDecimal expectedScore;

	@Column(name = "off_role_factor")
	private BigDecimal offRoleFactor;

	@Column(name = "formula_version", nullable = false, length = 16)
	private String formulaVersion;

	@Column(name = "note", length = 255)
	private String note;

	@Column(name = "created_at", insertable = false, updatable = false)
	private LocalDateTime createdAt;

	public static RatingHistory ofMatch(
			Long playerId,
			Long roomId,
			Long matchId,
			RatingScope scope,
			Lane lane,
			int ratingBefore,
			int ratingAfter,
			int rdBefore,
			int rdAfter,
			double expectedScore,
			BigDecimal offRoleFactor,
			String note) {
		RatingHistory history = new RatingHistory();
		history.playerId = playerId;
		history.roomId = roomId;
		history.matchId = matchId;
		history.scope = scope;
		history.lane = lane;
		history.reason = RatingChangeReason.MATCH;
		history.ratingBefore = ratingBefore;
		history.ratingAfter = ratingAfter;
		history.delta = ratingAfter - ratingBefore;
		history.rdBefore = (short) rdBefore;
		history.rdAfter = (short) rdAfter;
		history.expectedScore = BigDecimal.valueOf(expectedScore)
				.setScale(5, java.math.RoundingMode.HALF_UP);
		history.offRoleFactor = offRoleFactor;
		history.formulaVersion = FORMULA_V1;
		history.note = note;
		return history;
	}
}
