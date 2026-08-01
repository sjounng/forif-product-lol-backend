package com.scrim.lolscrim.domain.session;

import static com.scrim.lolscrim.global.error.ErrorCode.GUEST_NOT_FOUND;
import static com.scrim.lolscrim.global.error.ErrorCode.ROOM_NOT_FOUND;
import static com.scrim.lolscrim.global.error.ErrorCode.SESSION_ACCESS_DENIED;
import static com.scrim.lolscrim.global.error.ErrorCode.SESSION_ACTIVE_EXISTS;
import static com.scrim.lolscrim.global.error.ErrorCode.SESSION_CREATION_DENIED;
import static com.scrim.lolscrim.global.error.ErrorCode.SESSION_INVALID_ROSTER;
import static com.scrim.lolscrim.global.error.ErrorCode.SESSION_NOT_FOUND;
import static com.scrim.lolscrim.global.error.ErrorCode.SESSION_NOT_PROPOSED;
import static com.scrim.lolscrim.global.error.ErrorCode.SESSION_REVIEW_DENIED;
import static com.scrim.lolscrim.global.error.ErrorCode.USER_NOT_FOUND;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;

import com.scrim.lolscrim.domain.group.GuestSession;
import com.scrim.lolscrim.domain.group.GuestSessionRepository;
import com.scrim.lolscrim.domain.group.Room;
import com.scrim.lolscrim.domain.group.RoomMembershipRepository;
import com.scrim.lolscrim.domain.group.RoomRepository;
import com.scrim.lolscrim.domain.group.dto.GroupUserResponse;
import com.scrim.lolscrim.domain.session.dto.CreateSessionRequest;
import com.scrim.lolscrim.domain.session.dto.SessionMemberResponse;
import com.scrim.lolscrim.domain.session.dto.SessionResponse;
import com.scrim.lolscrim.domain.session.dto.SessionRosterMemberRequest;
import com.scrim.lolscrim.domain.session.dto.SessionTeamResponse;
import com.scrim.lolscrim.domain.session.dto.SessionViewerResponse;
import com.scrim.lolscrim.domain.user.User;
import com.scrim.lolscrim.domain.user.UserRepository;
import com.scrim.lolscrim.domain.player.RiotAccount;
import com.scrim.lolscrim.domain.player.RiotAccountRepository;
import com.scrim.lolscrim.global.error.ApiException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SessionService {

	private static final List<SessionStatus> ACTIVE_STATUSES = List.of(
			SessionStatus.PREPARING,
			SessionStatus.PROPOSED,
			SessionStatus.CONFIRMED,
			SessionStatus.IN_PROGRESS);

	private final ScrimSessionRepository sessionRepository;
	private final SessionTeamRepository teamRepository;
	private final SessionTeamMemberRepository teamMemberRepository;
	private final PlayerRepository playerRepository;
	private final RoomRepository roomRepository;
	private final RoomMembershipRepository membershipRepository;
	private final GuestSessionRepository guestRepository;
	private final UserRepository userRepository;
	private final RiotAccountRepository riotAccountRepository;
	private final Clock clock;

	@Transactional(readOnly = true)
	public List<SessionResponse> getSessions(Long userId, Long roomId) {
		requireRoomAccess(roomId, userId);
		return sessionRepository.findAllByRoomIdOrderByCreatedAtDesc(roomId).stream()
				.map(session -> toResponse(session, userId))
				.toList();
	}

	@Transactional(readOnly = true)
	public SessionResponse getSession(Long userId, Long sessionId) {
		ScrimSession session = findSession(sessionId);
		requireRoomAccess(session.getRoomId(), userId);
		return toResponse(session, userId);
	}

	@Transactional
	public SessionResponse createSession(Long userId, Long roomId, CreateSessionRequest request) {
		Room room = roomRepository.findById(roomId)
				.orElseThrow(() -> new ApiException(ROOM_NOT_FOUND, "그룹을 찾을 수 없습니다."));
		requireRoomAccess(roomId, userId);
		Long opponentCaptainId = request.opponentCaptainUserId();
		if (opponentCaptainId == null) {
			throw new ApiException(SESSION_CREATION_DENIED, "상대 팀장이 확정된 뒤 세션을 제안할 수 있습니다.");
		}
		if (userId.equals(opponentCaptainId)
				|| membershipRepository.findByRoomIdAndUserIdAndActiveTrue(roomId, opponentCaptainId).isEmpty()) {
			throw new ApiException(SESSION_CREATION_DENIED, "그룹의 두 팀장만 세션을 제안할 수 있습니다.");
		}
		if (sessionRepository.existsByRoomIdAndStatusIn(roomId, ACTIVE_STATUSES)) {
			throw new ApiException(SESSION_ACTIVE_EXISTS, "이미 준비 또는 진행 중인 세션이 있습니다.");
		}
		validateRoster(request.blueTeam(), request.redTeam());

		LocalDateTime now = LocalDateTime.now(clock);
		ScrimSession session;
		try {
			session = sessionRepository.saveAndFlush(ScrimSession.propose(
					roomId,
					userId,
					request.name(),
					request.matchFormat(),
					request.fearlessMode(),
					request.ratingEnabled(),
					now));
		} catch (DataIntegrityViolationException exception) {
			throw new ApiException(SESSION_ACTIVE_EXISTS, "이미 준비 또는 진행 중인 세션이 있습니다.");
		}

		Long blueCaptainId = request.creatorSide() == TeamSide.BLUE ? userId : opponentCaptainId;
		Long redCaptainId = request.creatorSide() == TeamSide.RED ? userId : opponentCaptainId;
		teamRepository.save(SessionTeam.create(
				session.getId(),
				TeamSide.BLUE,
				blueCaptainId,
				defaultTeamName(findUser(blueCaptainId)),
				now));
		teamRepository.save(SessionTeam.create(
				session.getId(),
				TeamSide.RED,
				redCaptainId,
				defaultTeamName(findUser(redCaptainId)),
				now));

		createRoster(session.getId(), roomId, userId, TeamSide.BLUE, request.blueTeam(), now);
		createRoster(session.getId(), roomId, userId, TeamSide.RED, request.redTeam(), now);
		return toResponse(session, userId);
	}

	@Transactional
	public SessionResponse accept(Long userId, Long sessionId) {
		ScrimSession session = findSessionForUpdate(sessionId);
		requireRoomAccess(session.getRoomId(), userId);
		requireProposed(session);
		requireReviewer(session, userId);
		session.confirm(LocalDateTime.now(clock));
		return toResponse(session, userId);
	}

	@Transactional
	public SessionResponse reject(Long userId, Long sessionId, String reason) {
		ScrimSession session = findSessionForUpdate(sessionId);
		requireRoomAccess(session.getRoomId(), userId);
		requireProposed(session);
		requireReviewer(session, userId);
		session.reject(reason, LocalDateTime.now(clock));
		return toResponse(session, userId);
	}

	@Transactional
	public SessionResponse cancel(Long userId, Long sessionId) {
		ScrimSession session = findSessionForUpdate(sessionId);
		requireRoomAccess(session.getRoomId(), userId);
		if (!userId.equals(session.getCreatedByUserId())) {
			throw new ApiException(SESSION_REVIEW_DENIED, "세션 제안자만 제안을 취소할 수 있습니다.");
		}
		if (!ACTIVE_STATUSES.contains(session.getStatus())) {
			throw new ApiException(SESSION_NOT_PROPOSED, "취소할 수 있는 세션 상태가 아닙니다.");
		}
		session.cancel(LocalDateTime.now(clock));
		return toResponse(session, userId);
	}

	@Transactional
	public SessionResponse renameTeam(Long userId, Long sessionId, String teamName) {
		ScrimSession session = findSessionForUpdate(sessionId);
		requireRoomAccess(session.getRoomId(), userId);
		SessionTeam team = teamRepository.findAllBySessionIdOrderBySideAsc(sessionId).stream()
				.filter(candidate -> candidate.getCaptainUserId().equals(userId))
				.findFirst()
				.orElseThrow(() -> new ApiException(
						SESSION_REVIEW_DENIED,
						"해당 세션의 팀장만 팀 이름을 변경할 수 있습니다."));
		String normalized = teamName == null ? "" : teamName.trim();
		if (normalized.isBlank() || normalized.length() > 30) {
			throw new ApiException(SESSION_REVIEW_DENIED, "팀 이름은 1~30자로 입력해 주세요.");
		}
		team.rename(normalized);
		return toResponse(session, userId);
	}

	private void createRoster(
			Long sessionId,
			Long roomId,
			Long actorUserId,
			TeamSide side,
			List<SessionRosterMemberRequest> requests,
			LocalDateTime now) {
		for (SessionRosterMemberRequest request : requests) {
			Player player = resolvePlayer(roomId, actorUserId, request, now);
			teamMemberRepository.save(SessionTeamMember.create(
					sessionId,
					side,
					player.getId(),
					request.lane(),
					now));
		}
	}

	private Player resolvePlayer(
			Long roomId,
			Long actorUserId,
			SessionRosterMemberRequest request,
			LocalDateTime now) {
		if (request.participantType() == ParticipantType.MEMBER) {
			membershipRepository.findByRoomIdAndUserIdAndActiveTrue(roomId, request.participantId())
					.orElseThrow(() -> new ApiException(SESSION_INVALID_ROSTER, "그룹 회원이 아닌 참가자가 포함되어 있습니다."));
			User user = userRepository.findById(request.participantId())
					.orElseThrow(() -> new ApiException(USER_NOT_FOUND, "사용자를 찾을 수 없습니다."));
			Player player = playerRepository.findByRoomIdAndMemberUserId(roomId, user.getId())
					.orElseGet(() -> playerRepository.save(Player.fromMember(
							roomId, user.getId(), user.getDisplayName(), actorUserId, now)));
			player.refreshDisplayName(user.getDisplayName(), now);
			return player;
		}
		if (request.participantType() == ParticipantType.PLAYER) {
			return playerRepository.findById(request.participantId())
					.filter(player -> player.getRoomId().equals(roomId))
					.filter(Player::isActive)
					.filter(player -> player.getRiotAccountId() != null)
					.orElseThrow(() -> new ApiException(
							SESSION_INVALID_ROSTER,
							"그룹에 등록된 Riot ID 참가자를 찾을 수 없습니다."));
		}

		GuestSession guest = guestRepository.findById(request.participantId())
				.filter(value -> value.getRoomId().equals(roomId))
				.orElseThrow(() -> new ApiException(GUEST_NOT_FOUND, "그룹 게스트를 찾을 수 없습니다."));
		if (!guest.isUsable(now)) {
			throw new ApiException(SESSION_INVALID_ROSTER, "퇴장 또는 차단된 게스트가 포함되어 있습니다.");
		}
		Player player = playerRepository.findByRoomIdAndGuestSessionId(roomId, guest.getId())
				.orElseGet(() -> playerRepository.save(Player.fromGuest(
						roomId, guest.getId(), guest.getNickname(), actorUserId, now)));
		player.refreshDisplayName(guest.getNickname(), now);
		return player;
	}

	private void validateRoster(
			List<SessionRosterMemberRequest> blue,
			List<SessionRosterMemberRequest> red) {
		if (blue == null || red == null || blue.size() != 5 || red.size() != 5) {
			throw new ApiException(SESSION_INVALID_ROSTER, "각 팀은 정확히 5명이어야 합니다.");
		}
		validateTeamLanes(blue);
		validateTeamLanes(red);
		Set<String> participants = new HashSet<>();
		for (SessionRosterMemberRequest member : concat(blue, red)) {
			String key = member.participantType() + ":" + member.participantId();
			if (!participants.add(key)) {
				throw new ApiException(SESSION_INVALID_ROSTER, "한 참가자를 두 팀에 중복 배치할 수 없습니다.");
			}
		}
	}

	private void validateTeamLanes(List<SessionRosterMemberRequest> team) {
		Set<Lane> lanes = EnumSet.noneOf(Lane.class);
		for (SessionRosterMemberRequest member : team) {
			if (member == null || member.lane() == null || !lanes.add(member.lane())) {
				throw new ApiException(SESSION_INVALID_ROSTER, "팀마다 5개 기본 라인을 한 명씩 배정해야 합니다.");
			}
		}
		if (!lanes.equals(EnumSet.allOf(Lane.class))) {
			throw new ApiException(SESSION_INVALID_ROSTER, "팀마다 5개 기본 라인을 한 명씩 배정해야 합니다.");
		}
	}

	private List<SessionRosterMemberRequest> concat(
			List<SessionRosterMemberRequest> blue,
			List<SessionRosterMemberRequest> red) {
		List<SessionRosterMemberRequest> all = new ArrayList<>(10);
		all.addAll(blue);
		all.addAll(red);
		return all;
	}

	private SessionResponse toResponse(ScrimSession session, Long viewerUserId) {
		List<SessionTeam> teams = teamRepository.findAllBySessionIdOrderBySideAsc(session.getId());
		List<SessionTeamMember> members = teamMemberRepository
				.findAllBySessionIdOrderBySideAscLaneAsc(session.getId());
		Map<Long, Player> players = new HashMap<>();
		playerRepository.findAllById(members.stream().map(SessionTeamMember::getPlayerId).toList())
				.forEach(player -> players.put(player.getId(), player));

		List<SessionTeamResponse> teamResponses = teams.stream()
				.map(team -> new SessionTeamResponse(
						team.getSide(),
						team.getTeamName(),
						GroupUserResponse.from(findUser(team.getCaptainUserId())),
						members.stream()
								.filter(member -> member.getSide() == team.getSide())
								.map(member -> toMemberResponse(member, players.get(member.getPlayerId())))
								.toList()))
				.toList();
		TeamSide captainSide = teams.stream()
				.filter(team -> team.getCaptainUserId().equals(viewerUserId))
				.map(SessionTeam::getSide)
				.findFirst()
				.orElse(null);
		boolean isCreator = viewerUserId.equals(session.getCreatedByUserId());
		boolean proposed = session.getStatus() == SessionStatus.PROPOSED;
		SessionViewerResponse viewer = new SessionViewerResponse(
				captainSide,
				captainSide != null && !isCreator && proposed,
				isCreator && ACTIVE_STATUSES.contains(session.getStatus()),
				captainSide != null && (session.getStatus() == SessionStatus.CONFIRMED
						|| session.getStatus() == SessionStatus.IN_PROGRESS));
		return new SessionResponse(
				session.getId(),
				session.getRoomId(),
				session.getName(),
				session.getMatchFormat(),
				session.getFearlessMode(),
				session.getStatus(),
				session.isRatingEnabled(),
				session.getGameCount(),
				session.getRejectionReason(),
				session.getProposedAt(),
				session.getConfirmedAt(),
				session.getCreatedAt(),
				teamResponses,
				viewer);
	}

	private SessionMemberResponse toMemberResponse(SessionTeamMember member, Player player) {
		RiotAccount account = player.getRiotAccountId() == null
				? null
				: riotAccountRepository.findById(player.getRiotAccountId()).orElse(null);
		return new SessionMemberResponse(
				player.getId(),
				player.getParticipantType(),
				player.getSourceId(),
				player.getDisplayName(),
				member.getLane(),
				account == null ? null : account.getPrimaryLane(),
				account == null ? null : account.getSecondaryLane());
	}

	private User findUser(Long userId) {
		return userRepository.findById(userId)
				.orElseThrow(() -> new ApiException(USER_NOT_FOUND, "사용자를 찾을 수 없습니다."));
	}

	private String defaultTeamName(User captain) {
		String displayName = captain.getDisplayName().trim();
		String shortened = displayName.length() > 28 ? displayName.substring(0, 28) : displayName;
		return shortened + " 팀";
	}


	private ScrimSession findSession(Long sessionId) {
		return sessionRepository.findById(sessionId)
				.orElseThrow(() -> new ApiException(SESSION_NOT_FOUND, "세션을 찾을 수 없습니다."));
	}

	private ScrimSession findSessionForUpdate(Long sessionId) {
		return sessionRepository.findByIdForUpdate(sessionId)
				.orElseThrow(() -> new ApiException(SESSION_NOT_FOUND, "세션을 찾을 수 없습니다."));
	}

	private void requireRoomAccess(Long roomId, Long userId) {
		if (membershipRepository.findByRoomIdAndUserIdAndActiveTrue(roomId, userId).isEmpty()) {
			throw new ApiException(SESSION_ACCESS_DENIED, "이 그룹의 세션을 조회할 권한이 없습니다.");
		}
	}

	private void requireProposed(ScrimSession session) {
		if (session.getStatus() != SessionStatus.PROPOSED) {
			throw new ApiException(SESSION_NOT_PROPOSED, "수락 또는 거절할 수 있는 제안 상태가 아닙니다.");
		}
	}

	private void requireReviewer(ScrimSession session, Long userId) {
		boolean isCaptain = teamRepository.findAllBySessionIdOrderBySideAsc(session.getId()).stream()
				.anyMatch(team -> team.getCaptainUserId().equals(userId));
		if (!isCaptain || userId.equals(session.getCreatedByUserId())) {
			throw new ApiException(SESSION_REVIEW_DENIED, "상대 팀장만 세션 제안을 검토할 수 있습니다.");
		}
	}
}
