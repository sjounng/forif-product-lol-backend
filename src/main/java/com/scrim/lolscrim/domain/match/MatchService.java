package com.scrim.lolscrim.domain.match;

import static com.scrim.lolscrim.global.error.ErrorCode.MATCH_ACTIVE_EXISTS;
import static com.scrim.lolscrim.global.error.ErrorCode.MATCH_CREATION_DENIED;
import static com.scrim.lolscrim.global.error.ErrorCode.MATCH_NOT_FOUND;
import static com.scrim.lolscrim.global.error.ErrorCode.MATCH_RESULT_INVALID_STATE;
import static com.scrim.lolscrim.global.error.ErrorCode.MATCH_RESULT_REVIEW_DENIED;
import static com.scrim.lolscrim.global.error.ErrorCode.MATCH_START_REQUEST_NOT_FOUND;
import static com.scrim.lolscrim.global.error.ErrorCode.MATCH_START_REQUEST_NOT_PENDING;
import static com.scrim.lolscrim.global.error.ErrorCode.MATCH_START_REQUEST_PENDING;
import static com.scrim.lolscrim.global.error.ErrorCode.MATCH_START_REVIEW_DENIED;
import static com.scrim.lolscrim.global.error.ErrorCode.ROOM_ACCESS_DENIED;
import static com.scrim.lolscrim.global.error.ErrorCode.SESSION_FINISH_DENIED;
import static com.scrim.lolscrim.global.error.ErrorCode.SESSION_NOT_FOUND;
import static com.scrim.lolscrim.global.error.ErrorCode.USER_NOT_FOUND;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.scrim.lolscrim.domain.champion.Champion;
import com.scrim.lolscrim.domain.champion.ChampionRepository;
import com.scrim.lolscrim.domain.draft.DraftAction;
import com.scrim.lolscrim.domain.draft.DraftActionRepository;
import com.scrim.lolscrim.domain.group.RoomMembershipRepository;
import com.scrim.lolscrim.domain.group.dto.GroupUserResponse;
import com.scrim.lolscrim.domain.match.dto.MatchDraftActionResponse;
import com.scrim.lolscrim.domain.match.dto.MatchOverviewResponse;
import com.scrim.lolscrim.domain.match.dto.MatchParticipantResponse;
import com.scrim.lolscrim.domain.match.dto.MatchParticipantResponse.ChampionSummary;
import com.scrim.lolscrim.domain.match.dto.MatchParticipantStatsRequest;
import com.scrim.lolscrim.domain.match.dto.MatchResponse;
import com.scrim.lolscrim.domain.match.dto.MatchScoreResponse;
import com.scrim.lolscrim.domain.match.dto.MatchStartRequestResponse;
import com.scrim.lolscrim.domain.match.dto.ProposeMatchResultRequest;
import com.scrim.lolscrim.domain.session.MatchFormat;
import com.scrim.lolscrim.domain.player.Player;
import com.scrim.lolscrim.domain.player.PlayerRepository;
import com.scrim.lolscrim.domain.session.ScrimSession;
import com.scrim.lolscrim.domain.session.ScrimSessionRepository;
import com.scrim.lolscrim.domain.session.SessionStatus;
import com.scrim.lolscrim.domain.session.SessionTeam;
import com.scrim.lolscrim.domain.session.SessionTeamMember;
import com.scrim.lolscrim.domain.session.SessionTeamMemberRepository;
import com.scrim.lolscrim.domain.session.SessionTeamRepository;
import com.scrim.lolscrim.domain.session.TeamSide;
import com.scrim.lolscrim.domain.user.User;
import com.scrim.lolscrim.domain.user.UserRepository;
import com.scrim.lolscrim.global.error.ApiException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MatchService {

	private static final EnumSet<MatchStatus> ACTIVE_MATCH_STATUSES = EnumSet.of(
			MatchStatus.PROPOSED,
			MatchStatus.ACCEPTED,
			MatchStatus.DRAFTING,
			MatchStatus.READY_TO_PLAY,
			MatchStatus.LIVE,
			MatchStatus.RESULT_PENDING,
			MatchStatus.RESULT_DISPUTED);

	private final ScrimSessionRepository sessionRepository;
	private final SessionTeamRepository teamRepository;
	private final SessionTeamMemberRepository teamMemberRepository;
	private final RoomMembershipRepository membershipRepository;
	private final MatchStartRequestRepository startRequestRepository;
	private final ScrimMatchRepository matchRepository;
	private final MatchParticipantRepository participantRepository;
	private final DraftRepository draftRepository;
	private final DraftActionRepository draftActionRepository;
	private final PlayerRepository playerRepository;
	private final ChampionRepository championRepository;
	private final UserRepository userRepository;
	private final Clock clock;

	@Transactional(readOnly = true)
	public MatchOverviewResponse getOverview(Long userId, Long sessionId) {
		ScrimSession session = requireSession(sessionId);
		requireSessionAccess(session, userId);
		return toOverview(session, userId);
	}

	@Transactional
	public MatchStartRequestResponse requestStart(Long userId, Long sessionId) {
		return requestStart(userId, sessionId, TeamSide.BLUE);
	}

	@Transactional
	public MatchStartRequestResponse requestStart(Long userId, Long sessionId, TeamSide blueTeamSide) {
		ScrimSession session = requireSessionForUpdate(sessionId);
		requireSessionAccess(session, userId);
		requireCaptain(sessionId, userId);
		requireSessionCanCreateMatch(session);
		requireNoActiveMatchOrRequest(sessionId);
		int gameNo = matchRepository.findFirstBySessionIdOrderByGameNoDesc(sessionId)
				.map(match -> match.getGameNo().intValue() + 1)
				.orElse(1);
		LocalDateTime now = LocalDateTime.now(clock);
		try {
			MatchStartRequest request = startRequestRepository.saveAndFlush(
					MatchStartRequest.propose(session, gameNo, userId, blueTeamSide, now));
			return toStartRequestResponse(request, userId, true);
		} catch (DataIntegrityViolationException exception) {
			throw new ApiException(MATCH_START_REQUEST_PENDING, "이미 응답 대기 중인 매치 시작 요청이 있습니다.");
		}
	}

	@Transactional
	public MatchResponse acceptStart(Long userId, Long requestId) {
		MatchStartRequest snapshot = requireStartRequest(requestId);
		ScrimSession session = requireSessionForUpdate(snapshot.getSessionId());
		requireSessionAccess(session, userId);
		MatchStartRequest request = requireStartRequestForUpdate(requestId);
		requirePending(request);
		requireOpposingCaptain(session.getId(), request.getProposedByUserId(), userId);
		requireSessionCanCreateMatch(session);
		if (matchRepository.existsBySessionIdAndStatusIn(session.getId(), ACTIVE_MATCH_STATUSES)) {
			throw new ApiException(MATCH_ACTIVE_EXISTS, "완료되지 않은 매치가 있어 새 매치를 시작할 수 없습니다.");
		}

		int expectedGameNo = matchRepository.findFirstBySessionIdOrderByGameNoDesc(session.getId())
				.map(match -> match.getGameNo().intValue() + 1)
				.orElse(1);
		if (request.getGameNo().intValue() != expectedGameNo) {
			throw new ApiException(MATCH_CREATION_DENIED, "매치 번호가 현재 세션 진행 상태와 일치하지 않습니다.");
		}

		List<SessionTeamMember> roster = teamMemberRepository
				.findAllBySessionIdOrderBySideAscLaneAsc(session.getId());
		if (roster.size() != 10) {
			throw new ApiException(MATCH_CREATION_DENIED, "확정된 세션 로스터가 정확히 10명이 아닙니다.");
		}
		LocalDateTime now = LocalDateTime.now(clock);
		ScrimMatch match;
		try {
			match = matchRepository.saveAndFlush(ScrimMatch.createDrafting(
					session.getId(), session.getRoomId(), expectedGameNo, request.getBlueTeamSide(), now));
		} catch (DataIntegrityViolationException exception) {
			throw new ApiException(MATCH_ACTIVE_EXISTS, "동일한 매치가 이미 생성되었습니다.");
		}
		participantRepository.saveAll(roster.stream()
				.map(member -> MatchParticipant.from(
						match.getId(),
						session.getRoomId(),
						member,
						match.matchSideForSessionTeam(member.getSide())))
				.toList());
		Draft draft = draftRepository.save(Draft.create(match.getId(), session.getId(), now));
		request.accept(userId, match.getId(), now);
		session.startMatchFlow(now);
		return toMatchResponse(match, draft.getId(), userId, true);
	}

	@Transactional
	public MatchStartRequestResponse rejectStart(Long userId, Long requestId) {
		MatchStartRequest snapshot = requireStartRequest(requestId);
		ScrimSession session = requireSessionForUpdate(snapshot.getSessionId());
		requireSessionAccess(session, userId);
		MatchStartRequest request = requireStartRequestForUpdate(requestId);
		requirePending(request);
		requireOpposingCaptain(session.getId(), request.getProposedByUserId(), userId);
		request.reject(userId, LocalDateTime.now(clock));
		return toStartRequestResponse(request, userId, true);
	}

	@Transactional
	public MatchStartRequestResponse cancelStart(Long userId, Long requestId) {
		MatchStartRequest snapshot = requireStartRequest(requestId);
		ScrimSession session = requireSessionForUpdate(snapshot.getSessionId());
		requireSessionAccess(session, userId);
		MatchStartRequest request = requireStartRequestForUpdate(requestId);
		requirePending(request);
		requireCaptain(session.getId(), userId);
		if (!request.getProposedByUserId().equals(userId)) {
			throw new ApiException(MATCH_START_REVIEW_DENIED, "매치 시작 요청자만 요청을 취소할 수 있습니다.");
		}
		request.cancel(LocalDateTime.now(clock));
		return toStartRequestResponse(request, userId, true);
	}

	@Transactional
	public MatchResponse startLive(Long userId, Long matchId) {
		ScrimMatch snapshot = requireMatch(matchId);
		ScrimSession session = requireSessionForUpdate(snapshot.getSessionId());
		requireSessionAccess(session, userId);
		requireCaptain(session.getId(), userId);
		ScrimMatch match = requireMatchForUpdate(matchId);
		if (match.getStatus() != MatchStatus.READY_TO_PLAY) {
			throw new ApiException(MATCH_RESULT_INVALID_STATE, "Draft가 완료된 매치만 경기를 시작할 수 있습니다.");
		}
		Draft draft = draftRepository.findByMatchId(matchId)
				.orElseThrow(() -> new ApiException(MATCH_CREATION_DENIED, "매치 Draft를 찾을 수 없습니다."));
		if (draft.getStatus() != DraftStatus.COMPLETED) {
			throw new ApiException(MATCH_RESULT_INVALID_STATE, "양 팀 Draft가 아직 완료되지 않았습니다.");
		}
		match.start(LocalDateTime.now(clock));
		return toMatchResponse(match, draft.getId(), userId, true);
	}

	@Transactional
	public MatchResponse proposeResult(
			Long userId,
			Long matchId,
			ProposeMatchResultRequest request) {
		ScrimMatch snapshot = requireMatch(matchId);
		ScrimSession session = requireSessionForUpdate(snapshot.getSessionId());
		requireSessionAccess(session, userId);
		requireCaptain(session.getId(), userId);
		ScrimMatch match = requireMatchForUpdate(matchId);
		if (match.getStatus() != MatchStatus.LIVE
				&& match.getStatus() != MatchStatus.RESULT_DISPUTED) {
			throw new ApiException(MATCH_RESULT_INVALID_STATE, "진행 중이거나 결과 분쟁 중인 매치만 결과를 입력할 수 있습니다.");
		}
		recordParticipantStats(matchId, request.participantStats());
		match.proposeResult(
				userId,
				request.winnerSide(),
				request.riotMatchId(),
				LocalDateTime.now(clock));
		return toMatchResponse(match, requireDraft(matchId).getId(), userId, true);
	}

	@Transactional
	public MatchResponse acceptResult(Long userId, Long matchId) {
		ScrimMatch snapshot = requireMatch(matchId);
		ScrimSession session = requireSessionForUpdate(snapshot.getSessionId());
		requireSessionAccess(session, userId);
		requireCaptain(session.getId(), userId);
		ScrimMatch match = requireMatchForUpdate(matchId);
		requireResultReviewer(match, userId);

		long blueWins = completedWins(session.getId(), TeamSide.BLUE);
		long redWins = completedWins(session.getId(), TeamSide.RED);
		TeamSide proposedWinningTeam = match.sessionTeamForMatchSide(match.getProposedWinnerSide());
		if (proposedWinningTeam == TeamSide.BLUE) {
			blueWins++;
		} else {
			redWins++;
		}
		LocalDateTime now = LocalDateTime.now(clock);
		match.complete(now);
		participantRepository.findAllByMatchId(matchId)
				.forEach(participant -> participant.recordResult(match.getWinnerSide()));
		boolean finish = reachedWinCondition(session.getMatchFormat(), blueWins, redWins);
		session.recordCompletedMatch(finish, now);
		return toMatchResponse(match, requireDraft(matchId).getId(), userId, true);
	}

	@Transactional
	public MatchResponse rejectResult(Long userId, Long matchId) {
		ScrimMatch snapshot = requireMatch(matchId);
		ScrimSession session = requireSessionForUpdate(snapshot.getSessionId());
		requireSessionAccess(session, userId);
		requireCaptain(session.getId(), userId);
		ScrimMatch match = requireMatchForUpdate(matchId);
		requireResultReviewer(match, userId);
		match.disputeResult(LocalDateTime.now(clock));
		return toMatchResponse(match, requireDraft(matchId).getId(), userId, true);
	}

	@Transactional
	public MatchOverviewResponse finishUnlimited(Long userId, Long sessionId) {
		ScrimSession session = requireSessionForUpdate(sessionId);
		requireSessionAccess(session, userId);
		if (session.getMatchFormat() != MatchFormat.UNLIMITED
				|| session.getStatus() != SessionStatus.IN_PROGRESS
				|| !userId.equals(session.getCreatedByUserId())) {
			throw new ApiException(SESSION_FINISH_DENIED, "제한 없음 세션의 생성자만 진행 중인 세션을 종료할 수 있습니다.");
		}
		if (matchRepository.existsBySessionIdAndStatusIn(sessionId, ACTIVE_MATCH_STATUSES)
				|| startRequestRepository.existsBySessionIdAndStatus(
						sessionId, MatchStartRequestStatus.PENDING)) {
			throw new ApiException(SESSION_FINISH_DENIED, "진행 또는 합의 대기 중인 매치를 먼저 마무리해 주세요.");
		}
		session.finish(LocalDateTime.now(clock));
		return toOverview(session, userId);
	}

	private MatchOverviewResponse toOverview(ScrimSession session, Long viewerUserId) {
		boolean viewerIsCaptain = isCaptain(session.getId(), viewerUserId);
		List<ScrimMatch> matches = matchRepository.findAllBySessionIdOrderByGameNoAsc(session.getId());
		List<Long> matchIds = matches.stream().map(ScrimMatch::getId).toList();
		Map<Long, Long> draftIds = new HashMap<>();
		draftRepository.findAllByMatchIdIn(matchIds)
				.forEach(draft -> draftIds.put(draft.getMatchId(), draft.getId()));
		Map<Long, List<MatchParticipantResponse>> participantsByMatch = participantResponsesByMatch(matchIds);
		Map<Long, List<MatchDraftActionResponse>> actionsByDraft = draftActionResponsesByDraft(
				draftIds.values().stream().toList());
		MatchStartRequestResponse pending = startRequestRepository
				.findFirstBySessionIdAndStatusOrderByCreatedAtDesc(
						session.getId(), MatchStartRequestStatus.PENDING)
				.map(request -> toStartRequestResponse(request, viewerUserId, viewerIsCaptain))
				.orElse(null);
		long blueWins = matches.stream()
				.filter(match -> match.getStatus() == MatchStatus.COMPLETED)
				.filter(match -> match.winningSessionTeamSide() == TeamSide.BLUE)
				.count();
		long redWins = matches.stream()
				.filter(match -> match.getStatus() == MatchStatus.COMPLETED)
				.filter(match -> match.winningSessionTeamSide() == TeamSide.RED)
				.count();
		boolean activeMatch = matches.stream().anyMatch(match -> ACTIVE_MATCH_STATUSES.contains(match.getStatus()));
		boolean canRequest = viewerIsCaptain
				&& (session.getStatus() == SessionStatus.CONFIRMED
						|| session.getStatus() == SessionStatus.IN_PROGRESS)
				&& !activeMatch
				&& pending == null
				&& !reachedWinCondition(session.getMatchFormat(), blueWins, redWins);
		boolean canFinish = session.getMatchFormat() == MatchFormat.UNLIMITED
				&& session.getStatus() == SessionStatus.IN_PROGRESS
				&& viewerUserId.equals(session.getCreatedByUserId())
				&& !activeMatch
				&& pending == null;
		return new MatchOverviewResponse(
				session.getId(),
				new MatchScoreResponse(blueWins, redWins),
				pending,
				matches.stream()
						.map(match -> toMatchResponse(
								match,
								draftIds.get(match.getId()),
								viewerUserId,
								viewerIsCaptain,
								participantsByMatch.getOrDefault(match.getId(), List.of()),
								draftIds.get(match.getId()) == null
										? List.of()
										: actionsByDraft.getOrDefault(draftIds.get(match.getId()), List.of())))
						.toList(),
				canRequest,
				canFinish);
	}

	private MatchStartRequestResponse toStartRequestResponse(
			MatchStartRequest request,
			Long viewerUserId,
			boolean viewerIsCaptain) {
		Map<TeamSide, SessionTeam> teams = teamsBySide(request.getSessionId());
		return MatchStartRequestResponse.from(
				request,
				GroupUserResponse.from(findUser(request.getProposedByUserId())),
				viewerUserId,
				viewerIsCaptain,
				teams.get(request.getBlueTeamSide()).getTeamName(),
				teams.get(opposite(request.getBlueTeamSide())).getTeamName());
	}

	private MatchResponse toMatchResponse(
			ScrimMatch match,
			Long draftId,
			Long viewerUserId,
			boolean viewerIsCaptain) {
		List<MatchParticipantResponse> participants = participantResponsesByMatch(List.of(match.getId()))
				.getOrDefault(match.getId(), List.of());
		List<MatchDraftActionResponse> draftActions = draftId == null
				? List.of()
				: draftActionResponsesByDraft(List.of(draftId)).getOrDefault(draftId, List.of());
		return toMatchResponse(match, draftId, viewerUserId, viewerIsCaptain, participants, draftActions);
	}

	private MatchResponse toMatchResponse(
			ScrimMatch match,
			Long draftId,
			Long viewerUserId,
			boolean viewerIsCaptain,
			List<MatchParticipantResponse> participants,
			List<MatchDraftActionResponse> draftActions) {
		Map<TeamSide, SessionTeam> teams = teamsBySide(match.getSessionId());
		return MatchResponse.from(
				match,
				draftId,
				viewerUserId,
				viewerIsCaptain,
				teams.get(match.sessionTeamForMatchSide(TeamSide.BLUE)).getTeamName(),
				teams.get(match.sessionTeamForMatchSide(TeamSide.RED)).getTeamName(),
				participants,
				draftActions);
	}

	private void recordParticipantStats(
			Long matchId,
			List<MatchParticipantStatsRequest> requestedStats) {
		List<MatchParticipant> participants = participantRepository.findAllByMatchId(matchId);
		Map<Long, MatchParticipantStatsRequest> statsByPlayer = new HashMap<>();
		for (MatchParticipantStatsRequest stats : requestedStats) {
			if (statsByPlayer.put(stats.playerId(), stats) != null) {
				throw new ApiException(MATCH_RESULT_INVALID_STATE, "한 참가자의 KDA가 중복 입력되었습니다.");
			}
		}
		Set<Long> participantIds = participants.stream()
				.map(MatchParticipant::getPlayerId)
				.collect(java.util.stream.Collectors.toSet());
		if (participants.size() != 10 || !participantIds.equals(statsByPlayer.keySet())) {
			throw new ApiException(MATCH_RESULT_INVALID_STATE, "매치 참가자 10명의 KDA를 모두 입력해 주세요.");
		}
		participants.forEach(participant -> {
			MatchParticipantStatsRequest stats = statsByPlayer.get(participant.getPlayerId());
			participant.recordKda(stats.kills(), stats.deaths(), stats.assists());
		});
	}

	private Map<Long, List<MatchDraftActionResponse>> draftActionResponsesByDraft(List<Long> draftIds) {
		if (draftIds.isEmpty()) {
			return Map.of();
		}
		List<DraftAction> actions = draftActionRepository
				.findAllByDraftIdInOrderByDraftIdAscStepNoAsc(draftIds);
		Map<Integer, Champion> champions = new HashMap<>();
		championRepository.findAllById(actions.stream()
				.map(DraftAction::getChampionId)
				.filter(java.util.Objects::nonNull)
				.collect(java.util.stream.Collectors.toSet()))
				.forEach(champion -> champions.put(champion.getId(), champion));

		Map<Long, List<MatchDraftActionResponse>> result = new HashMap<>();
		for (DraftAction action : actions) {
			Champion champion = champions.get(action.getChampionId());
			ChampionSummary championSummary = champion == null ? null : new ChampionSummary(
					champion.getId(),
					champion.getRiotId(),
					champion.getNameKo(),
					champion.getImageUrl());
			MatchDraftActionResponse response = new MatchDraftActionResponse(
					action.getStepNo().intValue(),
					action.getSide(),
					action.getActionType(),
					championSummary,
					action.getPlayerId(),
					action.isAuto());
			result.computeIfAbsent(action.getDraftId(), ignored -> new java.util.ArrayList<>())
					.add(response);
		}
		return result;
	}

	private Map<Long, List<MatchParticipantResponse>> participantResponsesByMatch(List<Long> matchIds) {
		if (matchIds.isEmpty()) {
			return Map.of();
		}
		List<MatchParticipant> participants = participantRepository
				.findAllByMatchIdInOrderByMatchIdAscSideAscLaneAsc(matchIds);
		Map<Long, Player> players = new HashMap<>();
		playerRepository.findAllById(participants.stream().map(MatchParticipant::getPlayerId).collect(java.util.stream.Collectors.toSet()))
				.forEach(player -> players.put(player.getId(), player));
		Map<Integer, Champion> champions = new HashMap<>();
		championRepository.findAllById(participants.stream()
				.map(MatchParticipant::getChampionId)
				.filter(java.util.Objects::nonNull)
				.collect(java.util.stream.Collectors.toSet()))
				.forEach(champion -> champions.put(champion.getId(), champion));

		Map<Long, List<MatchParticipantResponse>> result = new HashMap<>();
		for (MatchParticipant participant : participants) {
			Player player = players.get(participant.getPlayerId());
			Champion champion = champions.get(participant.getChampionId());
			ChampionSummary championSummary = champion == null ? null : new ChampionSummary(
					champion.getId(),
					champion.getRiotId(),
					champion.getNameKo(),
					champion.getImageUrl());
			MatchParticipantResponse response = new MatchParticipantResponse(
					participant.getPlayerId(),
					player == null ? "알 수 없는 참가자" : player.getDisplayName(),
					participant.getSide(),
					participant.getLane(),
					championSummary,
					participant.getKills(),
					participant.getDeaths(),
					participant.getAssists());
			result.computeIfAbsent(participant.getMatchId(), ignored -> new java.util.ArrayList<>())
					.add(response);
		}
		return result;
	}

	private Map<TeamSide, SessionTeam> teamsBySide(Long sessionId) {
		Map<TeamSide, SessionTeam> teams = new java.util.EnumMap<>(TeamSide.class);
		teamRepository.findAllBySessionIdOrderBySideAsc(sessionId)
				.forEach(team -> teams.put(team.getSide(), team));
		return teams;
	}

	private TeamSide opposite(TeamSide side) {
		return side == TeamSide.BLUE ? TeamSide.RED : TeamSide.BLUE;
	}

	private void requireSessionCanCreateMatch(ScrimSession session) {
		if (session.getStatus() != SessionStatus.CONFIRMED
				&& session.getStatus() != SessionStatus.IN_PROGRESS) {
			throw new ApiException(MATCH_CREATION_DENIED, "확정 또는 진행 중인 세션에서만 매치를 시작할 수 있습니다.");
		}
		if (reachedWinCondition(
				session.getMatchFormat(),
				completedWins(session.getId(), TeamSide.BLUE),
				completedWins(session.getId(), TeamSide.RED))) {
			throw new ApiException(MATCH_CREATION_DENIED, "승리 조건을 달성하여 종료된 세션입니다.");
		}
	}

	private void requireNoActiveMatchOrRequest(Long sessionId) {
		if (matchRepository.existsBySessionIdAndStatusIn(sessionId, ACTIVE_MATCH_STATUSES)) {
			throw new ApiException(MATCH_ACTIVE_EXISTS, "완료되지 않은 매치가 있습니다.");
		}
		if (startRequestRepository.existsBySessionIdAndStatus(
				sessionId, MatchStartRequestStatus.PENDING)) {
			throw new ApiException(MATCH_START_REQUEST_PENDING, "이미 응답 대기 중인 매치 시작 요청이 있습니다.");
		}
	}

	private void requireResultReviewer(ScrimMatch match, Long userId) {
		if (match.getStatus() != MatchStatus.RESULT_PENDING) {
			throw new ApiException(MATCH_RESULT_INVALID_STATE, "확인 대기 중인 결과가 없습니다.");
		}
		if (userId.equals(match.getResultProposedByUserId())) {
			throw new ApiException(MATCH_RESULT_REVIEW_DENIED, "결과를 입력한 팀장은 자신의 결과를 확인할 수 없습니다.");
		}
	}

	private void requirePending(MatchStartRequest request) {
		if (request.getStatus() != MatchStartRequestStatus.PENDING) {
			throw new ApiException(MATCH_START_REQUEST_NOT_PENDING, "이미 처리된 매치 시작 요청입니다.");
		}
	}

	private void requireOpposingCaptain(Long sessionId, Long proposerId, Long userId) {
		requireCaptain(sessionId, userId);
		if (proposerId.equals(userId)) {
			throw new ApiException(MATCH_START_REVIEW_DENIED, "요청자는 자신의 매치 시작 요청을 수락하거나 거절할 수 없습니다.");
		}
	}

	private SessionTeam requireCaptain(Long sessionId, Long userId) {
		return teamRepository.findAllBySessionIdOrderBySideAsc(sessionId).stream()
				.filter(team -> team.getCaptainUserId().equals(userId))
				.findFirst()
				.orElseThrow(() -> new ApiException(
						MATCH_START_REVIEW_DENIED,
						"세션의 두 팀장만 매치 흐름을 변경할 수 있습니다."));
	}

	private boolean isCaptain(Long sessionId, Long userId) {
		return teamRepository.findAllBySessionIdOrderBySideAsc(sessionId).stream()
				.anyMatch(team -> team.getCaptainUserId().equals(userId));
	}

	private void requireSessionAccess(ScrimSession session, Long userId) {
		if (membershipRepository.findByRoomIdAndUserIdAndActiveTrue(session.getRoomId(), userId).isEmpty()) {
			throw new ApiException(ROOM_ACCESS_DENIED, "이 세션의 매치를 조회할 권한이 없습니다.");
		}
	}

	private long completedWins(Long sessionId, TeamSide side) {
		return matchRepository.findAllBySessionIdOrderByGameNoAsc(sessionId).stream()
				.filter(match -> match.getStatus() == MatchStatus.COMPLETED)
				.filter(match -> match.winningSessionTeamSide() == side)
				.count();
	}

	private boolean reachedWinCondition(MatchFormat format, long blueWins, long redWins) {
		return switch (format) {
			case BEST_OF_3 -> blueWins >= 2 || redWins >= 2;
			case BEST_OF_5 -> blueWins >= 3 || redWins >= 3;
			case UNLIMITED -> false;
		};
	}

	private Draft requireDraft(Long matchId) {
		return draftRepository.findByMatchId(matchId)
				.orElseThrow(() -> new ApiException(MATCH_CREATION_DENIED, "매치 Draft를 찾을 수 없습니다."));
	}

	private ScrimSession requireSession(Long sessionId) {
		return sessionRepository.findById(sessionId)
				.orElseThrow(() -> new ApiException(SESSION_NOT_FOUND, "세션을 찾을 수 없습니다."));
	}

	private ScrimSession requireSessionForUpdate(Long sessionId) {
		return sessionRepository.findByIdForUpdate(sessionId)
				.orElseThrow(() -> new ApiException(SESSION_NOT_FOUND, "세션을 찾을 수 없습니다."));
	}

	private MatchStartRequest requireStartRequest(Long requestId) {
		return startRequestRepository.findById(requestId)
				.orElseThrow(() -> new ApiException(
						MATCH_START_REQUEST_NOT_FOUND,
						"매치 시작 요청을 찾을 수 없습니다."));
	}

	private MatchStartRequest requireStartRequestForUpdate(Long requestId) {
		return startRequestRepository.findByIdForUpdate(requestId)
				.orElseThrow(() -> new ApiException(
						MATCH_START_REQUEST_NOT_FOUND,
						"매치 시작 요청을 찾을 수 없습니다."));
	}

	private ScrimMatch requireMatch(Long matchId) {
		return matchRepository.findById(matchId)
				.orElseThrow(() -> new ApiException(MATCH_NOT_FOUND, "매치를 찾을 수 없습니다."));
	}

	private ScrimMatch requireMatchForUpdate(Long matchId) {
		return matchRepository.findByIdForUpdate(matchId)
				.orElseThrow(() -> new ApiException(MATCH_NOT_FOUND, "매치를 찾을 수 없습니다."));
	}

	private User findUser(Long userId) {
		return userRepository.findById(userId)
				.orElseThrow(() -> new ApiException(USER_NOT_FOUND, "사용자를 찾을 수 없습니다."));
	}
}
