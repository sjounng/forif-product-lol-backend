package com.scrim.lolscrim.domain.match;

import java.math.BigDecimal;

import com.scrim.lolscrim.domain.session.Lane;
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

	@Column(name = "assigned_from", nullable = false)
	private String assignedFrom;

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

	public static MatchParticipant from(
			Long matchId,
			Long roomId,
			SessionTeamMember member) {
		return from(matchId, roomId, member, member.getSide());
	}

	public static MatchParticipant from(
			Long matchId,
			Long roomId,
			SessionTeamMember member,
			TeamSide matchSide) {
		MatchParticipant participant = new MatchParticipant();
		participant.matchId = matchId;
		participant.playerId = member.getPlayerId();
		participant.roomId = roomId;
		participant.side = matchSide;
		participant.lane = member.getLane();
		participant.assignedFrom = "PRIMARY";
		participant.offRoleFactor = BigDecimal.ONE;
		return participant;
	}

	public void recordResult(TeamSide winnerSide) {
		win = side == winnerSide;
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
