package com.scrim.lolscrim.domain.match;

import java.math.BigDecimal;

import com.scrim.lolscrim.domain.player.Lane;
import com.scrim.lolscrim.domain.session.SessionTeamMember;
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
@Table(name = "match_participants")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MatchParticipant {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "match_id", nullable = false)
	private Long matchId;

	@Column(name = "player_id", nullable = false)
	private Long playerId;

	@Column(name = "room_id", nullable = false)
	private Long roomId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private TeamSide side;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Lane lane;

	@Enumerated(EnumType.STRING)
	@Column(name = "assigned_from", nullable = false)
	private AssignedFrom assignedFrom;

	@Column(name = "off_role_factor", nullable = false)
	private BigDecimal offRoleFactor;

	@Column(name = "is_win")
	private Boolean win;

	@Column(name = "champion_id", columnDefinition = "SMALLINT UNSIGNED")
	private Integer championId;

	@Column(columnDefinition = "SMALLINT UNSIGNED")
	private Integer kills;

	@Column(columnDefinition = "SMALLINT UNSIGNED")
	private Integer deaths;

	@Column(columnDefinition = "SMALLINT UNSIGNED")
	private Integer assists;

	/** 점수 반영 전후 스냅샷 (§4.2-B). 결과 무효화 시 되돌릴 근거이자 "왜 이만큼 움직였나"의 답. */
	@Column(name = "rating_before")
	private Integer ratingBefore;

	@Column(name = "rating_after")
	private Integer ratingAfter;

	@Column(name = "rating_delta")
	private Integer ratingDelta;

	/**
	 * 배정 라인·오프롤 계수는 매치 생성 시점에 확정해야 하므로 반드시 명시해서 만든다.
	 * 기본값을 주는 짧은 오버로드를 두면 새 매치 생성 경로가 그걸 잡아도 컴파일 에러 없이
	 * 전원 PRIMARY(1.000)로 기록되고, DB DEFAULT 'PRIMARY' 와 겹쳐 어느 층에서도 안 잡힌다.
	 */
	public static MatchParticipant from(
			Long matchId,
			Long roomId,
			SessionTeamMember member,
			TeamSide matchSide,
			AssignedFrom assignedFrom) {
		MatchParticipant participant = new MatchParticipant();
		participant.matchId = matchId;
		participant.playerId = member.getPlayerId();
		participant.roomId = roomId;
		participant.side = matchSide;
		participant.lane = member.getLane();
		participant.assignedFrom = assignedFrom;
		participant.offRoleFactor = assignedFrom.offRoleFactor();
		return participant;
	}

	public void recordResult(TeamSide winnerSide) {
		win = side == winnerSide;
	}

	/** 점수 반영 전후를 이 판의 참가 기록에 새긴다 (§4.2-B 감사 기록). */
	public void recordRatingChange(int ratingBefore, int ratingAfter) {
		this.ratingBefore = ratingBefore;
		this.ratingAfter = ratingAfter;
		this.ratingDelta = ratingAfter - ratingBefore;
	}

	public void assignChampion(Integer championId) {
		this.championId = championId;
	}

	public void recordKda(int kills, int deaths, int assists) {
		this.kills = kills;
		this.deaths = deaths;
		this.assists = assists;
	}
}
