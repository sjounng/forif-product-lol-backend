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
@Table(name = "session_team_members")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SessionTeamMember {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "scrim_session_id", nullable = false)
	private Long sessionId;

	@Enumerated(EnumType.STRING)
	@Column(name = "side", nullable = false)
	private TeamSide side;

	@Column(name = "player_id", nullable = false)
	private Long playerId;

	@Enumerated(EnumType.STRING)
	@Column(name = "lane", nullable = false)
	private Lane lane;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	public static SessionTeamMember create(
			Long sessionId,
			TeamSide side,
			Long playerId,
			Lane lane,
			LocalDateTime now) {
		SessionTeamMember member = new SessionTeamMember();
		member.sessionId = sessionId;
		member.side = side;
		member.playerId = playerId;
		member.lane = lane;
		member.createdAt = now;
		return member;
	}
}
