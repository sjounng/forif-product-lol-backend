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
@Table(name = "session_teams")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SessionTeam {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "scrim_session_id", nullable = false)
	private Long sessionId;

	@Enumerated(EnumType.STRING)
	@Column(name = "side", nullable = false)
	private TeamSide side;

	@Column(name = "team_name", nullable = false, length = 30)
	private String teamName;

	@Column(name = "captain_user_id", nullable = false)
	private Long captainUserId;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	public static SessionTeam create(Long sessionId, TeamSide side, Long captainUserId, LocalDateTime now) {
		return create(sessionId, side, captainUserId, side.name() + " 팀", now);
	}

	public static SessionTeam create(
			Long sessionId,
			TeamSide side,
			Long captainUserId,
			String teamName,
			LocalDateTime now) {
		SessionTeam team = new SessionTeam();
		team.sessionId = sessionId;
		team.side = side;
		team.captainUserId = captainUserId;
		team.teamName = teamName;
		team.createdAt = now;
		return team;
	}

	public void rename(String teamName) {
		this.teamName = teamName;
	}
}
