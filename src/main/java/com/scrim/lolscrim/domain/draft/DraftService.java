package com.scrim.lolscrim.domain.draft;

import static com.scrim.lolscrim.global.error.ErrorCode.DRAFT_ACCESS_DENIED;
import static com.scrim.lolscrim.global.error.ErrorCode.DRAFT_ASSIGNMENT_CONFIRMED;
import static com.scrim.lolscrim.global.error.ErrorCode.DRAFT_ASSIGNMENT_INVALID;
import static com.scrim.lolscrim.global.error.ErrorCode.DRAFT_CHAMPION_INVALID;
import static com.scrim.lolscrim.global.error.ErrorCode.DRAFT_CHAMPION_UNAVAILABLE;
import static com.scrim.lolscrim.global.error.ErrorCode.DRAFT_INVALID_STATE;
import static com.scrim.lolscrim.global.error.ErrorCode.DRAFT_NOT_FOUND;
import static com.scrim.lolscrim.global.error.ErrorCode.DRAFT_PLAYER_INVALID;
import static com.scrim.lolscrim.global.error.ErrorCode.DRAFT_STEP_MISMATCH;
import static com.scrim.lolscrim.global.error.ErrorCode.DRAFT_VERSION_CONFLICT;
import static com.scrim.lolscrim.global.error.ErrorCode.MATCH_NOT_FOUND;
import static com.scrim.lolscrim.global.error.ErrorCode.SESSION_NOT_FOUND;
import static com.scrim.lolscrim.global.error.ErrorCode.USER_NOT_FOUND;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;

import com.scrim.lolscrim.domain.champion.Champion;
import com.scrim.lolscrim.domain.champion.ChampionRepository;
import com.scrim.lolscrim.domain.draft.dto.AssignChampionRequest;
import com.scrim.lolscrim.domain.draft.dto.ConfirmAssignmentRequest;
import com.scrim.lolscrim.domain.draft.dto.DraftStateResponse;
import com.scrim.lolscrim.domain.draft.dto.DraftStateResponse.DraftAssignmentResponse;
import com.scrim.lolscrim.domain.draft.dto.DraftStateResponse.DraftChampionResponse;
import com.scrim.lolscrim.domain.draft.dto.DraftStateResponse.DraftHoverResponse;
import com.scrim.lolscrim.domain.draft.dto.DraftStateResponse.DraftPlayerResponse;
import com.scrim.lolscrim.domain.draft.dto.DraftStateResponse.DraftStepResponse;
import com.scrim.lolscrim.domain.draft.dto.DraftStateResponse.DraftTeamResponse;
import com.scrim.lolscrim.domain.draft.dto.DraftStateResponse.LockedChampionResponse;
import com.scrim.lolscrim.domain.draft.dto.DraftStateResponse.SessionSummary;
import com.scrim.lolscrim.domain.draft.dto.DraftStateResponse.ViewerResponse;
import com.scrim.lolscrim.domain.draft.dto.LockDraftRequest;
import com.scrim.lolscrim.domain.draft.dto.HoverDraftRequest;
import com.scrim.lolscrim.domain.draft.dto.ReadyDraftRequest;
import com.scrim.lolscrim.domain.draft.realtime.DraftEventCommitted;
import com.scrim.lolscrim.domain.group.RoomMembership;
import com.scrim.lolscrim.domain.group.RoomMembershipRepository;
import com.scrim.lolscrim.domain.match.Draft;
import com.scrim.lolscrim.domain.match.DraftRepository;
import com.scrim.lolscrim.domain.match.DraftStatus;
import com.scrim.lolscrim.domain.match.MatchParticipant;
import com.scrim.lolscrim.domain.match.MatchParticipantRepository;
import com.scrim.lolscrim.domain.match.MatchStatus;
import com.scrim.lolscrim.domain.match.ScrimMatch;
import com.scrim.lolscrim.domain.match.ScrimMatchRepository;
import com.scrim.lolscrim.domain.session.Player;
import com.scrim.lolscrim.domain.session.PlayerRepository;
import com.scrim.lolscrim.domain.session.FearlessMode;
import com.scrim.lolscrim.domain.session.ScrimSession;
import com.scrim.lolscrim.domain.session.ScrimSessionRepository;
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
public class DraftService {

	private final DraftRepository draftRepository;
	private final DraftRulesetStepRepository rulesetStepRepository;
	private final DraftActionRepository actionRepository;
	private final DraftAssignmentRepository assignmentRepository;
	private final DraftHoverRepository hoverRepository;
	private final DraftEventRepository eventRepository;
	private final SessionChampionPoolRepository championPoolRepository;
	private final ScrimMatchRepository matchRepository;
	private final MatchParticipantRepository matchParticipantRepository;
	private final ScrimSessionRepository sessionRepository;
	private final SessionTeamRepository teamRepository;
	private final SessionTeamMemberRepository teamMemberRepository;
	private final PlayerRepository playerRepository;
	private final ChampionRepository championRepository;
	private final UserRepository userRepository;
	private final RoomMembershipRepository membershipRepository;
	private final ApplicationEventPublisher applicationEventPublisher;
	private final Clock clock;

	@Transactional(readOnly = true)
	public DraftStateResponse getDraft(Long viewerUserId, Long draftId) {
		Draft draft = getDraft(draftId);
		return response(draft, viewerUserId, now());
	}

	@Transactional
	public DraftStateResponse ready(Long viewerUserId, Long draftId, ReadyDraftRequest request) {
		Draft draft = getDraftForUpdate(draftId);
		assertVersion(draft, request.expectedVersion());
		TeamSide side = captainSide(draft, viewerUserId);
		if (draft.getStatus() != DraftStatus.WAITING && draft.getStatus() != DraftStatus.READY) {
			throw new ApiException(DRAFT_INVALID_STATE, "READY를 확인할 수 있는 상태가 아닙니다.");
		}
		if (draft.isReady(side)) {
			throw new ApiException(DRAFT_INVALID_STATE, "이미 READY를 확인했습니다.");
		}

		LocalDateTime now = now();
		draft.ready(side, now);
		recordEvent(draft, "READY_UPDATED", Map.of("side", side.name()), now);
		return response(draft, viewerUserId, now);
	}

	@Transactional
	public DraftStateResponse lock(Long viewerUserId, Long draftId, LockDraftRequest request) {
		Draft draft = getDraftForUpdate(draftId);
		assertVersion(draft, request.expectedVersion());
		if (draft.getStatus() != DraftStatus.IN_PROGRESS) {
			throw new ApiException(DRAFT_INVALID_STATE, "현재는 BAN/PICK을 확정할 수 없습니다.");
		}
		if (draft.getCurrentStep().intValue() != request.stepNo()) {
			throw new ApiException(DRAFT_STEP_MISMATCH, "현재 Draft 단계와 요청 단계가 일치하지 않습니다.");
		}

		DraftRulesetStep step = rulesetStepRepository
				.findByRulesetIdAndStepNo(draft.getRulesetId(), request.stepNo().byteValue())
				.orElseThrow(() -> new ApiException(DRAFT_STEP_MISMATCH, "Draft 단계를 찾을 수 없습니다."));
		TeamSide side = captainSide(draft, viewerUserId);
		if (side != step.getSide()) {
			throw new ApiException(DRAFT_ACCESS_DENIED, "현재 차례의 팀장만 확정할 수 있습니다.");
		}
		LocalDateTime now = now();
		if (draft.isTurnExpired(side, now)) {
			throw new ApiException(DRAFT_INVALID_STATE, "현재 BAN/PICK 차례의 시간이 만료되었습니다.");
		}

		Champion champion = championRepository.findById(request.championId())
				.filter(Champion::isEnabled)
				.orElseThrow(() -> new ApiException(DRAFT_CHAMPION_INVALID, "사용할 수 없는 챔피언입니다."));
		if (actionRepository.existsByDraftIdAndChampionId(draftId, champion.getId())
				|| championPoolRepository.findAllBySessionId(draft.getSessionId()).stream()
						.anyMatch(pool -> pool.getChampionId().equals(champion.getId()))) {
			throw new ApiException(DRAFT_CHAMPION_UNAVAILABLE, "이미 선택되었거나 피어리스로 제한된 챔피언입니다.");
		}
		if (step.getActionType() == DraftActionType.BAN && request.playerId() != null) {
			throw new ApiException(DRAFT_PLAYER_INVALID, "BAN 단계에는 선수를 지정할 수 없습니다.");
		}
		hoverRepository.deleteByDraftIdAndSide(draftId, side);

		Long playerId = request.playerId();
		if (step.getActionType() == DraftActionType.PICK && playerId == null) {
			playerId = defaultPickPlayer(draft, side);
		}
		if (playerId != null) {
			assertRosterPlayer(draft, side, playerId);
		}
		actionRepository.saveAndFlush(DraftAction.lock(
				draftId,
				step,
				champion.getId(),
				playerId,
				viewerUserId,
				false,
				now));
		if (step.getActionType() == DraftActionType.PICK) {
			assignInternal(draft, side, playerId, champion.getId(), viewerUserId, false, now);
		}

		int finalStep = rulesetStepRepository.findAllByRulesetIdOrderByStepNoAsc(draft.getRulesetId()).size();
		draft.lockCurrentStep(request.stepNo() == finalStep, side, now);
		recordEvent(draft, "ACTION_LOCKED", lockPayload(step, champion.getId(), playerId), now);
		return response(draft, viewerUserId, now);
	}

	@Transactional
	public DraftStateResponse hover(Long viewerUserId, Long draftId, HoverDraftRequest request) {
		Draft draft = getDraftForUpdate(draftId);
		assertVersion(draft, request.expectedVersion());
		if (draft.getStatus() != DraftStatus.IN_PROGRESS
				|| draft.getCurrentStep().intValue() != request.stepNo()) {
			throw new ApiException(DRAFT_STEP_MISMATCH, "현재 Draft 단계와 요청 단계가 일치하지 않습니다.");
		}
		DraftRulesetStep step = rulesetStepRepository
				.findByRulesetIdAndStepNo(draft.getRulesetId(), request.stepNo().byteValue())
				.orElseThrow(() -> new ApiException(DRAFT_STEP_MISMATCH, "Draft 단계를 찾을 수 없습니다."));
		TeamSide side = captainSide(draft, viewerUserId);
		if (side != step.getSide()) {
			throw new ApiException(DRAFT_ACCESS_DENIED, "현재 차례의 팀장만 hover할 수 있습니다.");
		}

		if (request.championId() != null) {
			Champion champion = championRepository.findById(request.championId())
					.filter(Champion::isEnabled)
					.orElseThrow(() -> new ApiException(DRAFT_CHAMPION_INVALID, "사용할 수 없는 챔피언입니다."));
			if (actionRepository.existsByDraftIdAndChampionId(draftId, champion.getId())
					|| championPoolRepository.findAllBySessionId(draft.getSessionId()).stream()
							.anyMatch(pool -> pool.getChampionId().equals(champion.getId()))) {
				throw new ApiException(DRAFT_CHAMPION_UNAVAILABLE, "이미 선택되었거나 피어리스로 제한된 챔피언입니다.");
			}
		}

		LocalDateTime now = now();
		if (request.championId() == null) {
			hoverRepository.deleteByDraftIdAndSide(draftId, side);
		} else {
			DraftHover hover = hoverRepository.findByDraftIdAndSide(draftId, side)
					.orElseGet(() -> DraftHover.create(
							draftId, side, request.stepNo(), request.championId(), now));
			hover.update(request.stepNo(), request.championId(), now);
			hoverRepository.save(hover);
		}
		Map<String, Object> payload = new HashMap<>();
		payload.put("side", side.name());
		payload.put("stepNo", request.stepNo());
		if (request.championId() != null) {
			payload.put("championId", request.championId());
		}
		recordEvent(draft, "HOVER_UPDATED", payload, now);
		return response(draft, viewerUserId, now);
	}

	@Transactional
	public DraftStateResponse assign(
			Long viewerUserId,
			Long draftId,
			AssignChampionRequest request) {
		Draft draft = getDraftForUpdate(draftId);
		assertVersion(draft, request.expectedVersion());
		TeamSide side = captainSide(draft, viewerUserId);
		assertAssignmentMutable(draft, side);
		assertRosterPlayer(draft, side, request.playerId());
		assertPickedBySide(draftId, side, request.championId());

		LocalDateTime now = now();
		assignInternal(draft, side, request.playerId(), request.championId(), viewerUserId, false, now);
		draft.assignmentChanged();
		recordEvent(draft, "ASSIGNMENT_UPDATED", Map.of(
				"side", side.name(),
				"playerId", request.playerId(),
				"championId", request.championId()), now);
		return response(draft, viewerUserId, now);
	}

	@Transactional
	public DraftStateResponse confirmAssignment(
			Long viewerUserId,
			Long draftId,
			ConfirmAssignmentRequest request) {
		Draft draft = getDraftForUpdate(draftId);
		assertVersion(draft, request.expectedVersion());
		TeamSide side = captainSide(draft, viewerUserId);
		if (draft.getStatus() != DraftStatus.ASSIGNING) {
			throw new ApiException(DRAFT_INVALID_STATE, "선수 배정 단계가 아닙니다.");
		}
		if (draft.isAssignmentConfirmed(side)) {
			throw new ApiException(DRAFT_ASSIGNMENT_CONFIRMED, "이미 배정을 확정했습니다.");
		}
		assertCompleteAssignment(draft, side);

		LocalDateTime now = now();
		draft.confirmAssignment(side);
		if (draft.assignmentsConfirmed()) {
			completeDraft(draft, now);
			recordEvent(draft, "DRAFT_COMPLETED", Map.of("confirmedBy", side.name()), now);
		} else {
			recordEvent(draft, "ASSIGNMENT_CONFIRMED", Map.of("side", side.name()), now);
		}
		return response(draft, viewerUserId, now);
	}

	@Transactional
	public void autoCompleteExpiredAssignments(Long draftId) {
		Draft draft = draftRepository.findByIdForUpdate(draftId).orElse(null);
		LocalDateTime now = now();
		if (draft == null
				|| draft.getStatus() != DraftStatus.ASSIGNING
				|| draft.getAssignmentDeadlineAt() == null
				|| draft.getAssignmentDeadlineAt().isAfter(now)) {
			return;
		}

		for (TeamSide side : TeamSide.values()) {
			if (draft.isAssignmentConfirmed(side)) {
				continue;
			}
			autoAssignMissing(draft, side, now);
			assertCompleteAssignment(draft, side);
			draft.confirmAssignment(side);
			recordEvent(draft, "ASSIGNMENT_AUTO_COMPLETED", Map.of("side", side.name()), now);
		}
		if (draft.assignmentsConfirmed()) {
			completeDraft(draft, now);
			recordEvent(draft, "DRAFT_COMPLETED", Map.of("automatic", true), now);
		}
	}

	@Transactional
	public void autoCompleteExpiredTurn(Long draftId) {
		Draft draft = draftRepository.findByIdForUpdate(draftId).orElse(null);
		LocalDateTime now = now();
		if (draft == null
				|| draft.getStatus() != DraftStatus.IN_PROGRESS
				|| draft.getTurnDeadlineAt() == null) {
			return;
		}
		DraftRulesetStep step = rulesetStepRepository
				.findByRulesetIdAndStepNo(draft.getRulesetId(), draft.getCurrentStep())
				.orElse(null);
		if (step == null || !draft.isTurnExpired(step.getSide(), now)) {
			return;
		}

		Integer championId = null;
		Long playerId = null;
		if (step.getActionType() == DraftActionType.PICK) {
			championId = randomAvailableChampion(draft);
			playerId = defaultPickPlayer(draft, step.getSide());
		}
		hoverRepository.deleteByDraftIdAndSide(draftId, step.getSide());
		actionRepository.saveAndFlush(DraftAction.lock(
				draftId,
				step,
				championId,
				playerId,
				null,
				true,
				now));
		if (step.getActionType() == DraftActionType.PICK) {
			assignInternal(draft, step.getSide(), playerId, championId, null, true, now);
		}

		int finalStep = rulesetStepRepository.findAllByRulesetIdOrderByStepNoAsc(draft.getRulesetId()).size();
		draft.lockCurrentStep(step.getStepNo().intValue() == finalStep, step.getSide(), now);
		Map<String, Object> payload = lockPayload(step, championId, playerId);
		payload.put("auto", true);
		recordEvent(draft, "ACTION_LOCKED", payload, now);
	}

	private Integer randomAvailableChampion(Draft draft) {
		Set<Integer> unavailable = actionRepository.findAllByDraftIdOrderByStepNoAsc(draft.getId()).stream()
				.map(DraftAction::getChampionId)
				.filter(Objects::nonNull)
				.collect(Collectors.toSet());
		championPoolRepository.findAllBySessionId(draft.getSessionId()).stream()
				.map(SessionChampionPool::getChampionId)
				.forEach(unavailable::add);
		List<Integer> available = new ArrayList<>(championRepository.findAllByEnabledTrueOrderByNameKoAsc().stream()
				.map(Champion::getId)
				.filter(championId -> !unavailable.contains(championId))
				.toList());
		if (available.isEmpty()) {
			throw new ApiException(DRAFT_CHAMPION_UNAVAILABLE, "자동 선택할 수 있는 챔피언이 없습니다.");
		}
		Collections.shuffle(available);
		return available.getFirst();
	}

	private void autoAssignMissing(Draft draft, TeamSide side, LocalDateTime now) {
		List<SessionTeamMember> roster = roster(draft, side);
		List<Integer> picked = pickedChampions(draft.getId(), side);
		List<DraftAssignment> current = assignmentRepository.findAllByDraftIdOrderBySideAscPlayerIdAsc(draft.getId())
				.stream()
				.filter(assignment -> assignment.getSide() == side)
				.toList();
		Set<Long> assignedPlayers = current.stream()
				.map(DraftAssignment::getPlayerId)
				.collect(Collectors.toSet());
		Set<Integer> assignedChampions = current.stream()
				.map(DraftAssignment::getChampionId)
				.collect(Collectors.toSet());
		List<Long> remainingPlayers = roster.stream()
				.map(SessionTeamMember::getPlayerId)
				.filter(playerId -> !assignedPlayers.contains(playerId))
				.toList();
		List<Integer> remainingChampions = new ArrayList<>(picked.stream()
				.filter(championId -> !assignedChampions.contains(championId))
				.toList());
		Collections.shuffle(remainingChampions);
		for (int index = 0; index < remainingPlayers.size(); index++) {
			Long playerId = remainingPlayers.get(index);
			Integer championId = remainingChampions.get(index);
			assignmentRepository.save(DraftAssignment.create(
					draft.getId(), side, playerId, championId, null, true, now));
			draft.assignmentChanged();
			recordEvent(draft, "ASSIGNMENT_UPDATED", Map.of(
					"side", side.name(),
					"playerId", playerId,
					"championId", championId), now);
		}
	}

	private void completeDraft(Draft draft, LocalDateTime now) {
		List<DraftAssignment> assignments = assignmentRepository
				.findAllByDraftIdOrderBySideAscPlayerIdAsc(draft.getId());
		Map<Long, Integer> championByPlayer = assignments.stream()
				.collect(Collectors.toMap(DraftAssignment::getPlayerId, DraftAssignment::getChampionId));
		List<MatchParticipant> participants = matchParticipantRepository.findAllByMatchId(draft.getMatchId());
		if (participants.size() != 10 || !championByPlayer.keySet().containsAll(
				participants.stream().map(MatchParticipant::getPlayerId).toList())) {
			throw new ApiException(DRAFT_ASSIGNMENT_INVALID, "양 팀의 선수 배정이 완전하지 않습니다.");
		}
		participants.forEach(participant -> participant.assignChampion(championByPlayer.get(participant.getPlayerId())));

		ScrimMatch match = matchRepository.findByIdForUpdate(draft.getMatchId())
				.orElseThrow(() -> new ApiException(MATCH_NOT_FOUND, "매치를 찾을 수 없습니다."));
		if (match.getStatus() != MatchStatus.DRAFTING) {
			throw new ApiException(DRAFT_INVALID_STATE, "매치가 Draft 완료를 받을 수 없는 상태입니다.");
		}
		draft.complete(now);
		match.markReadyToPlay(now);
		applyFearlessPool(draft, match);
	}

	private void applyFearlessPool(Draft draft, ScrimMatch match) {
		ScrimSession session = sessionRepository.findById(draft.getSessionId())
				.orElseThrow(() -> new ApiException(SESSION_NOT_FOUND, "세션을 찾을 수 없습니다."));
		if (session.getFearlessMode() == FearlessMode.NONE) {
			return;
		}

		List<SessionChampionPool> additions = actionRepository
				.findAllByDraftIdOrderByStepNoAsc(draft.getId()).stream()
				.filter(action -> action.getChampionId() != null)
				.filter(action -> action.getActionType() == DraftActionType.PICK
						|| session.getFearlessMode() == FearlessMode.HARD_FEARLESS)
				.map(action -> SessionChampionPool.create(
						session.getId(),
						action.getChampionId(),
						action.getActionType().name(),
						match.getId()))
				.toList();
		championPoolRepository.saveAll(additions);
	}

	private void assertAssignmentMutable(Draft draft, TeamSide side) {
		if (draft.isAssignmentConfirmed(side)) {
			throw new ApiException(DRAFT_ASSIGNMENT_CONFIRMED, "확정한 팀의 배정은 변경할 수 없습니다.");
		}
		boolean allTeamPicksCompleted = actionRepository.countByDraftIdAndSideAndActionType(
				draft.getId(), side, DraftActionType.PICK) == 5;
		if (draft.getStatus() != DraftStatus.ASSIGNING
				&& !(draft.getStatus() == DraftStatus.IN_PROGRESS && allTeamPicksCompleted)) {
			throw new ApiException(DRAFT_INVALID_STATE, "아직 선수 배정을 변경할 수 없습니다.");
		}
	}

	private void assertCompleteAssignment(Draft draft, TeamSide side) {
		List<SessionTeamMember> roster = roster(draft, side);
		List<Integer> picks = pickedChampions(draft.getId(), side);
		List<DraftAssignment> assignments = assignmentRepository
				.findAllByDraftIdOrderBySideAscPlayerIdAsc(draft.getId()).stream()
				.filter(assignment -> assignment.getSide() == side)
				.toList();
		Set<Long> rosterIds = roster.stream().map(SessionTeamMember::getPlayerId).collect(Collectors.toSet());
		Set<Long> assignedPlayers = assignments.stream().map(DraftAssignment::getPlayerId).collect(Collectors.toSet());
		Set<Integer> assignedChampions = assignments.stream()
				.map(DraftAssignment::getChampionId)
				.collect(Collectors.toSet());
		if (rosterIds.size() != 5
				|| picks.size() != 5
				|| assignments.size() != 5
				|| !assignedPlayers.equals(rosterIds)
				|| !assignedChampions.equals(Set.copyOf(picks))) {
			throw new ApiException(DRAFT_ASSIGNMENT_INVALID, "5명의 선수와 5개의 픽을 일대일로 배정해야 합니다.");
		}
	}

	private void assignInternal(
			Draft draft,
			TeamSide side,
			Long playerId,
			Integer championId,
			Long actorUserId,
			boolean auto,
			LocalDateTime now) {
		DraftAssignment byPlayer = assignmentRepository.findByDraftIdAndPlayerId(draft.getId(), playerId).orElse(null);
		DraftAssignment byChampion = assignmentRepository
				.findByDraftIdAndChampionId(draft.getId(), championId).orElse(null);

		if (byPlayer != null && byChampion != null && byPlayer.getId().equals(byChampion.getId())) {
			return;
		}
		if (byPlayer != null && byChampion != null) {
			Integer previousChampion = byPlayer.getChampionId();
			Long previousPlayer = byChampion.getPlayerId();
			assignmentRepository.deleteAllInBatch(List.of(byPlayer, byChampion));
			assignmentRepository.saveAll(List.of(
					DraftAssignment.create(draft.getId(), side, playerId, championId, actorUserId, auto, now),
					DraftAssignment.create(draft.getId(), side, previousPlayer, previousChampion, actorUserId, auto, now)));
			return;
		}
		if (byChampion != null) {
			assignmentRepository.delete(byChampion);
			assignmentRepository.flush();
		}
		if (byPlayer == null) {
			assignmentRepository.save(DraftAssignment.create(
					draft.getId(), side, playerId, championId, actorUserId, auto, now));
		} else {
			byPlayer.reassign(championId, actorUserId, auto, now);
		}
	}

	private void assertPickedBySide(Long draftId, TeamSide side, Integer championId) {
		boolean picked = actionRepository.findAllByDraftIdOrderByStepNoAsc(draftId).stream()
				.anyMatch(action -> action.getSide() == side
						&& action.getActionType() == DraftActionType.PICK
						&& Objects.equals(action.getChampionId(), championId));
		if (!picked) {
			throw new ApiException(DRAFT_ASSIGNMENT_INVALID, "해당 팀이 PICK한 챔피언만 배정할 수 있습니다.");
		}
	}

	private void assertRosterPlayer(Draft draft, TeamSide side, Long playerId) {
		boolean exists = roster(draft, side).stream()
				.anyMatch(member -> member.getPlayerId().equals(playerId));
		if (!exists) {
			throw new ApiException(DRAFT_PLAYER_INVALID, "해당 팀의 고정 선수가 아닙니다.");
		}
	}

	private TeamSide captainSide(Draft draft, Long userId) {
		ScrimMatch match = matchRepository.findById(draft.getMatchId())
				.orElseThrow(() -> new ApiException(MATCH_NOT_FOUND, "매치를 찾을 수 없습니다."));
		TeamSide sessionTeamSide = teamRepository.findAllBySessionIdOrderBySideAsc(draft.getSessionId()).stream()
				.filter(team -> team.getCaptainUserId().equals(userId))
				.map(SessionTeam::getSide)
				.findFirst()
				.orElseThrow(() -> new ApiException(DRAFT_ACCESS_DENIED, "해당 세션의 팀장만 Draft를 조작할 수 있습니다."));
		return match.matchSideForSessionTeam(sessionTeamSide);
	}

	private void assertVersion(Draft draft, Integer expectedVersion) {
		if (!draft.getVersion().equals(expectedVersion)) {
			throw new ApiException(DRAFT_VERSION_CONFLICT, "Draft가 갱신되었습니다. 최신 상태를 다시 불러오세요.");
		}
	}

	private Draft getDraft(Long draftId) {
		return draftRepository.findById(draftId)
				.orElseThrow(() -> new ApiException(DRAFT_NOT_FOUND, "Draft를 찾을 수 없습니다."));
	}

	private Draft getDraftForUpdate(Long draftId) {
		return draftRepository.findByIdForUpdate(draftId)
				.orElseThrow(() -> new ApiException(DRAFT_NOT_FOUND, "Draft를 찾을 수 없습니다."));
	}

	private List<SessionTeamMember> roster(Draft draft, TeamSide matchSide) {
		ScrimMatch match = matchRepository.findById(draft.getMatchId())
				.orElseThrow(() -> new ApiException(MATCH_NOT_FOUND, "매치를 찾을 수 없습니다."));
		TeamSide sessionTeamSide = match.sessionTeamForMatchSide(matchSide);
		return teamMemberRepository.findAllBySessionIdOrderBySideAscLaneAsc(draft.getSessionId()).stream()
				.filter(member -> member.getSide() == sessionTeamSide)
				.toList();
	}

	private List<Integer> pickedChampions(Long draftId, TeamSide side) {
		return actionRepository.findAllByDraftIdOrderByStepNoAsc(draftId).stream()
				.filter(action -> action.getSide() == side && action.getActionType() == DraftActionType.PICK)
				.map(DraftAction::getChampionId)
				.filter(Objects::nonNull)
				.toList();
	}

	private Long defaultPickPlayer(Draft draft, TeamSide side) {
		int pickIndex = Math.toIntExact(actionRepository.countByDraftIdAndSideAndActionType(
				draft.getId(), side, DraftActionType.PICK));
		List<SessionTeamMember> teamRoster = roster(draft, side);
		if (pickIndex >= teamRoster.size()) {
			throw new ApiException(DRAFT_PLAYER_INVALID, "기본 픽 담당 선수를 찾을 수 없습니다.");
		}
		return teamRoster.get(pickIndex).getPlayerId();
	}

	private void recordEvent(
			Draft draft,
			String eventType,
			Map<String, Object> payload,
			LocalDateTime now) {
		int seq = draft.nextEventSeq();
		eventRepository.save(DraftEvent.create(
				draft.getId(), seq, draft.getVersion(), eventType, payload, now));
		applicationEventPublisher.publishEvent(new DraftEventCommitted(
				draft.getId(), seq, draft.getVersion(), eventType, Map.copyOf(payload)));
	}

	private Map<String, Object> lockPayload(
			DraftRulesetStep step,
			Integer championId,
			Long playerId) {
		Map<String, Object> payload = new HashMap<>();
		payload.put("stepNo", step.getStepNo().intValue());
		payload.put("side", step.getSide().name());
		payload.put("actionType", step.getActionType().name());
		if (championId != null) {
			payload.put("championId", championId);
		}
		if (playerId != null) {
			payload.put("playerId", playerId);
		}
		return payload;
	}

	private DraftStateResponse response(Draft draft, Long viewerUserId, LocalDateTime serverTime) {
		ScrimMatch match = matchRepository.findById(draft.getMatchId())
				.orElseThrow(() -> new ApiException(MATCH_NOT_FOUND, "매치를 찾을 수 없습니다."));
		ScrimSession session = sessionRepository.findById(draft.getSessionId())
				.orElseThrow(() -> new ApiException(SESSION_NOT_FOUND, "세션을 찾을 수 없습니다."));
		RoomMembership membership = membershipRepository
				.findByRoomIdAndUserIdAndActiveTrue(session.getRoomId(), viewerUserId)
				.orElseThrow(() -> new ApiException(DRAFT_ACCESS_DENIED, "그룹 참가자만 Draft를 조회할 수 있습니다."));

		List<SessionTeam> teams = teamRepository.findAllBySessionIdOrderBySideAsc(session.getId());
		List<SessionTeamMember> members = teamMemberRepository
				.findAllBySessionIdOrderBySideAscLaneAsc(session.getId());
		Map<Long, Player> players = playerRepository.findAllById(
				members.stream().map(SessionTeamMember::getPlayerId).toList()).stream()
				.collect(Collectors.toMap(Player::getId, Function.identity()));
		Map<Long, User> captains = userRepository.findAllById(
				teams.stream().map(SessionTeam::getCaptainUserId).toList()).stream()
				.collect(Collectors.toMap(User::getId, Function.identity()));

		TeamSide viewerSide = teams.stream()
				.filter(team -> team.getCaptainUserId().equals(viewerUserId))
				.map(team -> match.matchSideForSessionTeam(team.getSide()))
				.findFirst()
				.orElse(null);
		String viewerRole = viewerSide == null
				? viewerRole(membership, members, players, viewerUserId)
				: viewerSide.name() + "_CAPTAIN";

		List<DraftRulesetStep> stepDefinitions = rulesetStepRepository
				.findAllByRulesetIdOrderByStepNoAsc(draft.getRulesetId());
		List<DraftAction> actions = actionRepository.findAllByDraftIdOrderByStepNoAsc(draft.getId());
		Map<Byte, DraftAction> actionByStep = actions.stream()
				.collect(Collectors.toMap(DraftAction::getStepNo, Function.identity()));
		List<DraftAssignment> assignments = assignmentRepository
				.findAllByDraftIdOrderBySideAscPlayerIdAsc(draft.getId());
		List<SessionChampionPool> fearless = championPoolRepository.findAllBySessionId(session.getId()).stream()
				.filter(pool -> !match.getId().equals(pool.getUsedInMatchId()))
				.toList();
		DraftHover hover = hoverRepository.findFirstByDraftIdOrderByUpdatedAtDesc(draft.getId())
				.filter(candidate -> draft.getStatus() == DraftStatus.IN_PROGRESS
						&& candidate.getStepNo().equals(draft.getCurrentStep()))
				.orElse(null);

		Set<Integer> championIds = new LinkedHashSet<>();
		actions.stream().map(DraftAction::getChampionId).filter(Objects::nonNull).forEach(championIds::add);
		assignments.stream().map(DraftAssignment::getChampionId).forEach(championIds::add);
		fearless.stream().map(SessionChampionPool::getChampionId).forEach(championIds::add);
		if (hover != null && hover.getChampionId() != null) {
			championIds.add(hover.getChampionId());
		}
		Map<Integer, Champion> champions = championRepository.findAllById(championIds).stream()
				.collect(Collectors.toMap(Champion::getId, Function.identity()));

		EnumMap<TeamSide, DraftTeamResponse> teamResponses = new EnumMap<>(TeamSide.class);
		for (TeamSide matchSide : TeamSide.values()) {
			TeamSide sessionTeamSide = match.sessionTeamForMatchSide(matchSide);
			SessionTeam team = teams.stream()
					.filter(candidate -> candidate.getSide() == sessionTeamSide)
					.findFirst()
					.orElseThrow();
			User captain = captains.get(team.getCaptainUserId());
			List<DraftPlayerResponse> teamPlayers = members.stream()
					.filter(member -> member.getSide() == sessionTeamSide)
					.map(member -> new DraftPlayerResponse(
							member.getPlayerId(),
							players.get(member.getPlayerId()).getDisplayName(),
							member.getLane()))
					.toList();
			teamResponses.put(matchSide, new DraftTeamResponse(
					matchSide,
					team.getTeamName(),
					team.getCaptainUserId(),
					captain.getDisplayName(),
					draft.isReady(matchSide),
					teamPlayers));
		}

		List<DraftStepResponse> stepResponses = stepDefinitions.stream().map(step -> {
			DraftAction action = actionByStep.get(step.getStepNo());
			return new DraftStepResponse(
					step.getStepNo().intValue(),
					step.getSide(),
					step.getActionType(),
					step.getPhase().intValue(),
					action == null ? null : championResponse(champions.get(action.getChampionId())),
					action == null ? null : action.getPlayerId(),
					action != null && action.isAuto(),
					action == null ? null : action.getActedAt());
		}).toList();

		List<LockedChampionResponse> locked = new ArrayList<>();
		for (DraftAction action : actions) {
			if (action.getChampionId() != null) {
				locked.add(new LockedChampionResponse(
						championResponse(champions.get(action.getChampionId())),
						action.getActionType() == DraftActionType.BAN ? "CURRENT_BAN" : "CURRENT_PICK",
						match.getId(),
						action.getSide()));
			}
		}
		for (SessionChampionPool pool : fearless) {
			locked.add(new LockedChampionResponse(
					championResponse(champions.get(pool.getChampionId())),
					"BAN".equals(pool.getSource()) ? "PREVIOUS_MATCH_BAN" : "PREVIOUS_MATCH_PICK",
					pool.getUsedInMatchId(),
					null));
		}

		List<DraftAssignmentResponse> assignmentResponses = assignments.stream()
				.map(assignment -> {
					SessionTeamMember member = members.stream()
							.filter(candidate -> candidate.getPlayerId().equals(assignment.getPlayerId()))
							.findFirst()
							.orElseThrow();
					return new DraftAssignmentResponse(
							assignment.getSide(),
							assignment.getPlayerId(),
							players.get(assignment.getPlayerId()).getDisplayName(),
							member.getLane(),
							championResponse(champions.get(assignment.getChampionId())),
							assignment.isAuto());
				}).toList();

		DraftRulesetStep activeStep = draft.getStatus() == DraftStatus.IN_PROGRESS
				? stepDefinitions.stream()
						.filter(step -> step.getStepNo().equals(draft.getCurrentStep()))
						.findFirst().orElse(null)
				: null;
		long viewerAssignments = viewerSide == null ? 0 : assignments.stream()
				.filter(assignment -> assignment.getSide() == viewerSide).count();
		boolean viewerConfirmed = viewerSide != null && draft.isAssignmentConfirmed(viewerSide);
		ViewerResponse viewer = new ViewerResponse(
				viewerRole,
				viewerSide,
				viewerSide != null
						&& (draft.getStatus() == DraftStatus.WAITING || draft.getStatus() == DraftStatus.READY)
						&& !draft.isReady(viewerSide),
				viewerSide != null && activeStep != null && activeStep.getSide() == viewerSide,
				viewerSide != null
						&& !viewerConfirmed
						&& (draft.getStatus() == DraftStatus.ASSIGNING
								|| (draft.getStatus() == DraftStatus.IN_PROGRESS
										&& actionRepository.countByDraftIdAndSideAndActionType(
											draft.getId(), viewerSide, DraftActionType.PICK) == 5)),
				viewerSide != null
						&& draft.getStatus() == DraftStatus.ASSIGNING
						&& !viewerConfirmed
						&& viewerAssignments == 5);

		EnumMap<TeamSide, Boolean> confirmed = new EnumMap<>(TeamSide.class);
		confirmed.put(TeamSide.BLUE, draft.isAssignmentConfirmed(TeamSide.BLUE));
		confirmed.put(TeamSide.RED, draft.isAssignmentConfirmed(TeamSide.RED));

		return new DraftStateResponse(
				draft.getId(),
				draft.getVersion(),
				draft.getLastEventSeq(),
				serverTime,
				draft.getStatus(),
				new SessionSummary(session.getId(), session.getName(), match.getGameNo().intValue(), session.getFearlessMode()),
				teamResponses,
				stepResponses,
				draft.getCurrentStep().intValue(),
				hover == null ? null : new DraftHoverResponse(
						hover.getSide(),
						hover.getStepNo().intValue(),
						championResponse(champions.get(hover.getChampionId())),
						hover.getUpdatedAt()),
				draft.getTurnDeadlineAt(),
				draft.getAssignmentDeadlineAt(),
				activeStep == null
						? draft.getBlueReserveMs()
						: draft.reserveRemaining(TeamSide.BLUE, activeStep.getSide(), serverTime),
				activeStep == null
						? draft.getRedReserveMs()
						: draft.reserveRemaining(TeamSide.RED, activeStep.getSide(), serverTime),
				locked,
				fearless.stream().map(SessionChampionPool::getChampionId).distinct().toList(),
				assignmentResponses,
				confirmed,
				viewer);
	}

	private String viewerRole(
			RoomMembership membership,
			List<SessionTeamMember> members,
			Map<Long, Player> players,
			Long viewerUserId) {
		boolean sessionPlayer = members.stream()
				.map(member -> players.get(member.getPlayerId()))
				.anyMatch(player -> viewerUserId.equals(player.getMemberUserId()));
		return sessionPlayer ? "SESSION_PLAYER" : membership.getRole().name();
	}

	private DraftChampionResponse championResponse(Champion champion) {
		if (champion == null) {
			return null;
		}
		return new DraftChampionResponse(
				champion.getId(), champion.getRiotId(), champion.getNameKo(), champion.getImageUrl());
	}

	private LocalDateTime now() {
		return LocalDateTime.now(clock);
	}
}
