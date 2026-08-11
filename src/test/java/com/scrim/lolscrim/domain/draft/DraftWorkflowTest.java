package com.scrim.lolscrim.domain.draft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.context.ApplicationEventPublisher;

import com.scrim.lolscrim.domain.champion.Champion;
import com.scrim.lolscrim.domain.champion.ChampionRepository;
import com.scrim.lolscrim.domain.draft.dto.AssignChampionRequest;
import com.scrim.lolscrim.domain.draft.dto.ConfirmAssignmentRequest;
import com.scrim.lolscrim.domain.draft.dto.HoverDraftRequest;
import com.scrim.lolscrim.domain.draft.dto.LockDraftRequest;
import com.scrim.lolscrim.domain.draft.dto.ReadyDraftRequest;
import com.scrim.lolscrim.domain.group.RoomMembershipRepository;
import com.scrim.lolscrim.domain.group.RoomMembership;
import com.scrim.lolscrim.domain.match.Draft;
import com.scrim.lolscrim.domain.match.DraftRepository;
import com.scrim.lolscrim.domain.match.DraftStatus;
import com.scrim.lolscrim.domain.match.MatchParticipant;
import com.scrim.lolscrim.domain.match.MatchParticipantRepository;
import com.scrim.lolscrim.domain.match.MatchStatus;
import com.scrim.lolscrim.domain.match.ScrimMatch;
import com.scrim.lolscrim.domain.match.ScrimMatchRepository;
import com.scrim.lolscrim.domain.session.FearlessMode;
import com.scrim.lolscrim.domain.player.Lane;
import com.scrim.lolscrim.domain.session.MatchFormat;
import com.scrim.lolscrim.domain.player.Player;
import com.scrim.lolscrim.domain.player.PlayerRepository;
import com.scrim.lolscrim.domain.session.ScrimSession;
import com.scrim.lolscrim.domain.session.ScrimSessionRepository;
import com.scrim.lolscrim.domain.session.SessionTeam;
import com.scrim.lolscrim.domain.session.SessionTeamMember;
import com.scrim.lolscrim.domain.session.SessionTeamMemberRepository;
import com.scrim.lolscrim.domain.session.SessionTeamRepository;
import com.scrim.lolscrim.domain.session.TeamSide;
import com.scrim.lolscrim.domain.user.UserRepository;
import com.scrim.lolscrim.domain.user.User;
import com.scrim.lolscrim.global.error.ApiException;
import com.scrim.lolscrim.global.error.ErrorCode;

@ExtendWith(MockitoExtension.class)
class DraftWorkflowTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 1, 13, 0);

	@Mock private DraftRepository draftRepository;
	@Mock private DraftRulesetStepRepository rulesetStepRepository;
	@Mock private DraftActionRepository actionRepository;
	@Mock private DraftAssignmentRepository assignmentRepository;
	@Mock private DraftHoverRepository hoverRepository;
	@Mock private DraftEventRepository eventRepository;
	@Mock private SessionChampionPoolRepository championPoolRepository;
	@Mock private ScrimMatchRepository matchRepository;
	@Mock private MatchParticipantRepository matchParticipantRepository;
	@Mock private ScrimSessionRepository sessionRepository;
	@Mock private SessionTeamRepository teamRepository;
	@Mock private SessionTeamMemberRepository teamMemberRepository;
	@Mock private PlayerRepository playerRepository;
	@Mock private ChampionRepository championRepository;
	@Mock private UserRepository userRepository;
	@Mock private RoomMembershipRepository membershipRepository;
	@Mock private ApplicationEventPublisher applicationEventPublisher;

	private DraftService service;

	@BeforeEach
	void setUp() {
		Clock clock = Clock.fixed(Instant.parse("2026-08-01T04:00:00Z"), ZoneId.of("Asia/Seoul"));
		service = new DraftService(
				draftRepository,
				rulesetStepRepository,
				actionRepository,
				assignmentRepository,
				hoverRepository,
				eventRepository,
				championPoolRepository,
				matchRepository,
				matchParticipantRepository,
				sessionRepository,
				teamRepository,
				teamMemberRepository,
				playerRepository,
				championRepository,
				userRepository,
				membershipRepository,
				applicationEventPublisher,
				clock);
	}

	@Test
	void bothCaptainsReadyStartsDraftAtFirstStep() {
		Draft draft = draft();

		draft.ready(TeamSide.BLUE, NOW);
		draft.ready(TeamSide.RED, NOW.plusSeconds(1));

		assertThat(draft.getStatus()).isEqualTo(DraftStatus.IN_PROGRESS);
		assertThat(draft.getCurrentStep()).isEqualTo((byte) 1);
		assertThat(draft.getVersion()).isEqualTo(2);
		assertThat(draft.getStartedAt()).isEqualTo(NOW.plusSeconds(1));
		assertThat(draft.getTurnDeadlineAt()).isEqualTo(NOW.plusSeconds(31));
	}

	@Test
	void elapsedTurnConsumesOnlyTheActingTeamsReserveAndStartsNextTimer() {
		Draft draft = draft();
		draft.ready(TeamSide.BLUE, NOW);
		draft.ready(TeamSide.RED, NOW);

		draft.lockCurrentStep(false, TeamSide.BLUE, NOW.plusSeconds(35));

		assertThat(draft.getBlueReserveMs()).isEqualTo(25_000);
		assertThat(draft.getRedReserveMs()).isEqualTo(30_000);
		assertThat(draft.getCurrentStep()).isEqualTo((byte) 2);
		assertThat(draft.getTurnDeadlineAt()).isEqualTo(NOW.plusSeconds(65));
	}

	@Test
	void finalLockMovesDraftToNinetySecondAssignmentStage() {
		Draft draft = draft();
		draft.ready(TeamSide.BLUE, NOW);
		draft.ready(TeamSide.RED, NOW);
		ReflectionTestUtils.setField(draft, "currentStep", (byte) 20);

		draft.lockCurrentStep(true, NOW.plusMinutes(1));

		assertThat(draft.getStatus()).isEqualTo(DraftStatus.ASSIGNING);
		assertThat(draft.getAssignmentDeadlineAt()).isEqualTo(NOW.plusMinutes(1).plusSeconds(90));
	}

	@Test
	void staleVersionIsRejectedBeforeReadyMutation() {
		Draft draft = draft();
		when(draftRepository.findByIdForUpdate(60L)).thenReturn(Optional.of(draft));

		assertThatThrownBy(() -> service.ready(1L, 60L, new ReadyDraftRequest(3)))
				.isInstanceOfSatisfying(ApiException.class, exception ->
						assertThat(exception.getCode()).isEqualTo(ErrorCode.DRAFT_VERSION_CONFLICT));
	}

	@Test
	void opposingCaptainCannotLockCurrentSideStep() {
		Draft draft = inProgressDraft();
		DraftRulesetStep step = step(1, TeamSide.BLUE, DraftActionType.BAN);
		when(draftRepository.findByIdForUpdate(60L)).thenReturn(Optional.of(draft));
		when(rulesetStepRepository.findByRulesetIdAndStepNo("TOURNAMENT_STANDARD", (byte) 1))
				.thenReturn(Optional.of(step));
		when(teamRepository.findAllBySessionIdOrderBySideAsc(7L)).thenReturn(teams());

		assertThatThrownBy(() -> service.lock(2L, 60L, new LockDraftRequest(1, 266, null, 2)))
				.isInstanceOfSatisfying(ApiException.class, exception ->
						assertThat(exception.getCode()).isEqualTo(ErrorCode.DRAFT_ACCESS_DENIED));
	}

	@Test
	void duplicateChampionCannotBeLocked() {
		Draft draft = inProgressDraft();
		DraftRulesetStep step = step(1, TeamSide.BLUE, DraftActionType.BAN);
		Champion champion = mock(Champion.class);
		when(champion.isEnabled()).thenReturn(true);
		when(champion.getId()).thenReturn(266);
		when(draftRepository.findByIdForUpdate(60L)).thenReturn(Optional.of(draft));
		when(rulesetStepRepository.findByRulesetIdAndStepNo("TOURNAMENT_STANDARD", (byte) 1))
				.thenReturn(Optional.of(step));
		when(teamRepository.findAllBySessionIdOrderBySideAsc(7L)).thenReturn(teams());
		when(championRepository.findById(266)).thenReturn(Optional.of(champion));
		when(actionRepository.existsByDraftIdAndChampionId(60L, 266)).thenReturn(true);

		assertThatThrownBy(() -> service.lock(1L, 60L, new LockDraftRequest(1, 266, null, 2)))
				.isInstanceOfSatisfying(ApiException.class, exception ->
						assertThat(exception.getCode()).isEqualTo(ErrorCode.DRAFT_CHAMPION_UNAVAILABLE));
	}

	@Test
	void currentCaptainLocksBanAndAdvancesVersionedStep() {
		Draft draft = inProgressDraft();
		DraftRulesetStep current = step(1, TeamSide.BLUE, DraftActionType.BAN);
		List<DraftRulesetStep> definitions = steps();
		Champion champion = mock(Champion.class);
		when(champion.isEnabled()).thenReturn(true);
		when(champion.getId()).thenReturn(266);
		when(draftRepository.findByIdForUpdate(60L)).thenReturn(Optional.of(draft));
		when(rulesetStepRepository.findByRulesetIdAndStepNo("TOURNAMENT_STANDARD", (byte) 1))
				.thenReturn(Optional.of(current));
		when(rulesetStepRepository.findAllByRulesetIdOrderByStepNoAsc("TOURNAMENT_STANDARD"))
				.thenReturn(definitions);
		when(teamRepository.findAllBySessionIdOrderBySideAsc(7L)).thenReturn(teams());
		when(championRepository.findById(266)).thenReturn(Optional.of(champion));
		when(championPoolRepository.findAllBySessionId(7L)).thenReturn(List.of());
		when(actionRepository.findAllByDraftIdOrderByStepNoAsc(60L)).thenReturn(List.of());
		when(assignmentRepository.findAllByDraftIdOrderBySideAscPlayerIdAsc(60L)).thenReturn(List.of());
		stubSnapshot(1L);

		var response = service.lock(1L, 60L, new LockDraftRequest(1, 266, null, 2));

		assertThat(response.currentStep()).isEqualTo(2);
		assertThat(response.version()).isEqualTo(3);
		verify(actionRepository).saveAndFlush(org.mockito.ArgumentMatchers.any(DraftAction.class));
		verify(eventRepository).save(org.mockito.ArgumentMatchers.any(DraftEvent.class));
	}

	@Test
	void hoverPublishesSequenceWithoutChangingLockVersion() {
		Draft draft = inProgressDraft();
		DraftRulesetStep current = step(1, TeamSide.BLUE, DraftActionType.BAN);
		List<DraftRulesetStep> definitions = steps();
		Champion champion = champion(266);
		when(champion.isEnabled()).thenReturn(true);
		when(draftRepository.findByIdForUpdate(60L)).thenReturn(Optional.of(draft));
		when(rulesetStepRepository.findByRulesetIdAndStepNo("TOURNAMENT_STANDARD", (byte) 1))
				.thenReturn(Optional.of(current));
		when(rulesetStepRepository.findAllByRulesetIdOrderByStepNoAsc("TOURNAMENT_STANDARD"))
				.thenReturn(definitions);
		when(teamRepository.findAllBySessionIdOrderBySideAsc(7L)).thenReturn(teams());
		when(championRepository.findById(266)).thenReturn(Optional.of(champion));
		when(championRepository.findAllById(org.mockito.ArgumentMatchers.any()))
				.thenReturn(List.of(champion));
		when(championPoolRepository.findAllBySessionId(7L)).thenReturn(List.of());
		when(actionRepository.findAllByDraftIdOrderByStepNoAsc(60L)).thenReturn(List.of());
		when(assignmentRepository.findAllByDraftIdOrderBySideAscPlayerIdAsc(60L)).thenReturn(List.of());
		when(hoverRepository.findByDraftIdAndSide(60L, TeamSide.BLUE)).thenReturn(Optional.empty());
		when(hoverRepository.save(org.mockito.ArgumentMatchers.any(DraftHover.class)))
				.thenAnswer(invocation -> {
					DraftHover hover = invocation.getArgument(0);
					when(hoverRepository.findFirstByDraftIdOrderByUpdatedAtDesc(60L))
							.thenReturn(Optional.of(hover));
					return hover;
				});
		stubSnapshot(1L);

		var response = service.hover(1L, 60L, new HoverDraftRequest(1, 266, 2));

		assertThat(response.version()).isEqualTo(2);
		assertThat(response.lastEventSeq()).isEqualTo(1);
		assertThat(response.hover().champion().id()).isEqualTo(266);
	}

	@Test
	void captainAssignsOwnPickedChampion() {
		Draft draft = assigningDraft();
		List<DraftRulesetStep> definitions = steps();
		DraftAction pick = DraftAction.lock(
				60L, step(7, TeamSide.BLUE, DraftActionType.PICK), 266, null, 1L, false, NOW);
		List<DraftAssignment> assignments = new java.util.ArrayList<>();
		Champion champion = champion(266);
		when(draftRepository.findByIdForUpdate(60L)).thenReturn(Optional.of(draft));
		when(teamRepository.findAllBySessionIdOrderBySideAsc(7L)).thenReturn(teams());
		when(teamMemberRepository.findAllBySessionIdOrderBySideAscLaneAsc(7L)).thenReturn(roster());
		when(actionRepository.findAllByDraftIdOrderByStepNoAsc(60L)).thenReturn(List.of(pick));
		when(assignmentRepository.findByDraftIdAndPlayerId(60L, 1L)).thenReturn(Optional.empty());
		when(assignmentRepository.findByDraftIdAndChampionId(60L, 266)).thenReturn(Optional.empty());
		when(assignmentRepository.save(org.mockito.ArgumentMatchers.any(DraftAssignment.class)))
				.thenAnswer(invocation -> {
					DraftAssignment assignment = invocation.getArgument(0);
					assignments.add(assignment);
					return assignment;
				});
		when(assignmentRepository.findAllByDraftIdOrderBySideAscPlayerIdAsc(60L))
				.thenAnswer(invocation -> List.copyOf(assignments));
		when(rulesetStepRepository.findAllByRulesetIdOrderByStepNoAsc("TOURNAMENT_STANDARD"))
				.thenReturn(definitions);
		when(championRepository.findAllById(org.mockito.ArgumentMatchers.any()))
				.thenReturn(List.of(champion));
		stubSnapshot(1L);

		var response = service.assign(1L, 60L, new AssignChampionRequest(1L, 266, 3));

		assertThat(response.version()).isEqualTo(4);
		assertThat(response.assignments()).singleElement().satisfies(assignment -> {
			assertThat(assignment.playerId()).isEqualTo(1L);
			assertThat(assignment.champion().id()).isEqualTo(266);
		});
	}

	@Test
	void secondTeamConfirmationCompletesDraftAndMakesMatchReady() {
		Draft draft = assigningDraft();
		List<DraftRulesetStep> definitions = steps();
		draft.confirmAssignment(TeamSide.BLUE);
		List<SessionTeamMember> roster = roster();
		List<Champion> champions = IntStream.range(0, 10)
				.mapToObj(index -> champion(100 + index))
				.toList();
		List<DraftAction> picks = IntStream.range(0, 10)
				.mapToObj(index -> DraftAction.lock(
						60L,
						step(index + 1, index < 5 ? TeamSide.BLUE : TeamSide.RED, DraftActionType.PICK),
						100 + index,
						null,
						index < 5 ? 1L : 2L,
						false,
						NOW))
				.toList();
		List<DraftAssignment> assignments = IntStream.range(0, 10)
				.mapToObj(index -> DraftAssignment.create(
						60L,
						index < 5 ? TeamSide.BLUE : TeamSide.RED,
						(long) index + 1,
						100 + index,
						index < 5 ? 1L : 2L,
						false,
						NOW))
				.toList();
		ScrimMatch match = ScrimMatch.createDrafting(7L, 9L, 1, NOW.minusMinutes(10));
		ReflectionTestUtils.setField(match, "id", 50L);
		List<MatchParticipant> participants = roster.stream()
				.map(member -> MatchParticipant.from(50L, 9L, member))
				.toList();

		when(draftRepository.findByIdForUpdate(60L)).thenReturn(Optional.of(draft));
		when(teamRepository.findAllBySessionIdOrderBySideAsc(7L)).thenReturn(teams());
		when(teamMemberRepository.findAllBySessionIdOrderBySideAscLaneAsc(7L)).thenReturn(roster);
		when(actionRepository.findAllByDraftIdOrderByStepNoAsc(60L)).thenReturn(picks);
		when(assignmentRepository.findAllByDraftIdOrderBySideAscPlayerIdAsc(60L))
				.thenReturn(assignments);
		when(matchParticipantRepository.findAllByMatchId(50L)).thenReturn(participants);
		when(matchRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(match));
		when(rulesetStepRepository.findAllByRulesetIdOrderByStepNoAsc("TOURNAMENT_STANDARD"))
				.thenReturn(definitions);
		when(championRepository.findAllById(org.mockito.ArgumentMatchers.any())).thenReturn(champions);
		stubSnapshot(2L, FearlessMode.GLOBAL_FEARLESS);
		when(matchRepository.findById(50L)).thenReturn(Optional.of(match));

		var response = service.confirmAssignment(2L, 60L, new ConfirmAssignmentRequest(4));

		assertThat(response.status()).isEqualTo(DraftStatus.COMPLETED);
		assertThat(match.getStatus()).isEqualTo(MatchStatus.READY_TO_PLAY);
		assertThat(participants).allSatisfy(participant -> assertThat(participant.getChampionId()).isNotNull());
		verify(championPoolRepository).saveAll(org.mockito.ArgumentMatchers.argThat(entries -> {
			List<SessionChampionPool> pools = new java.util.ArrayList<>();
			entries.forEach(pools::add);
			return pools.size() == 10
					&& pools.stream().allMatch(pool -> "ANY".equals(pool.getSide()) && "PICK".equals(pool.getSource()));
		}));
	}

	@Test
	void captainCannotAssignOpponentPlayer() {
		Draft draft = assigningDraft();
		when(draftRepository.findByIdForUpdate(60L)).thenReturn(Optional.of(draft));
		when(teamRepository.findAllBySessionIdOrderBySideAsc(7L)).thenReturn(teams());
		when(teamMemberRepository.findAllBySessionIdOrderBySideAscLaneAsc(7L)).thenReturn(roster());

		assertThatThrownBy(() -> service.assign(1L, 60L, new AssignChampionRequest(6L, 266, 3)))
				.isInstanceOfSatisfying(ApiException.class, exception ->
						assertThat(exception.getCode()).isEqualTo(ErrorCode.DRAFT_PLAYER_INVALID));
	}

	@Test
	void incompleteAssignmentsCannotBeConfirmed() {
		Draft draft = assigningDraft();
		when(draftRepository.findByIdForUpdate(60L)).thenReturn(Optional.of(draft));
		when(teamRepository.findAllBySessionIdOrderBySideAsc(7L)).thenReturn(teams());
		when(teamMemberRepository.findAllBySessionIdOrderBySideAscLaneAsc(7L)).thenReturn(roster());
		when(actionRepository.findAllByDraftIdOrderByStepNoAsc(60L)).thenReturn(List.of());
		when(assignmentRepository.findAllByDraftIdOrderBySideAscPlayerIdAsc(60L)).thenReturn(List.of());

		assertThatThrownBy(() -> service.confirmAssignment(1L, 60L, new ConfirmAssignmentRequest(3)))
				.isInstanceOfSatisfying(ApiException.class, exception ->
						assertThat(exception.getCode()).isEqualTo(ErrorCode.DRAFT_ASSIGNMENT_INVALID));
	}

	private Draft draft() {
		Draft draft = Draft.create(50L, 7L, NOW.minusMinutes(5));
		ReflectionTestUtils.setField(draft, "id", 60L);
		return draft;
	}

	private Draft inProgressDraft() {
		Draft draft = draft();
		draft.ready(TeamSide.BLUE, NOW.minusSeconds(2));
		draft.ready(TeamSide.RED, NOW.minusSeconds(1));
		ScrimMatch match = ScrimMatch.createDrafting(7L, 9L, 1, NOW.minusMinutes(10));
		ReflectionTestUtils.setField(match, "id", 50L);
		lenient().when(matchRepository.findById(50L)).thenReturn(Optional.of(match));
		return draft;
	}

	private Draft assigningDraft() {
		Draft draft = inProgressDraft();
		draft.lockCurrentStep(true, NOW);
		return draft;
	}

	private DraftRulesetStep step(int stepNo, TeamSide side, DraftActionType actionType) {
		DraftRulesetStep step = mock(DraftRulesetStep.class);
		lenient().when(step.getStepNo()).thenReturn((byte) stepNo);
		lenient().when(step.getSide()).thenReturn(side);
		lenient().when(step.getActionType()).thenReturn(actionType);
		lenient().when(step.getPhase()).thenReturn((byte) 1);
		return step;
	}

	private List<DraftRulesetStep> steps() {
		return IntStream.rangeClosed(1, 20)
				.mapToObj(index -> step(
						index,
						index % 2 == 1 ? TeamSide.BLUE : TeamSide.RED,
						index <= 6 ? DraftActionType.BAN : DraftActionType.PICK))
				.toList();
	}

	private void stubSnapshot(Long viewerUserId) {
		stubSnapshot(viewerUserId, FearlessMode.NONE);
	}

	private void stubSnapshot(Long viewerUserId, FearlessMode fearlessMode) {
		ScrimSession session = ScrimSession.propose(
				9L,
				1L,
				"정기 내전",
				MatchFormat.BEST_OF_3,
				fearlessMode,
				true,
				NOW.minusHours(1));
		ReflectionTestUtils.setField(session, "id", 7L);
		ScrimMatch match = ScrimMatch.createDrafting(7L, 9L, 1, NOW.minusMinutes(10));
		ReflectionTestUtils.setField(match, "id", 50L);
		lenient().when(matchRepository.findById(50L)).thenReturn(Optional.of(match));
		lenient().when(sessionRepository.findById(7L)).thenReturn(Optional.of(session));

		RoomMembership membership = mock(RoomMembership.class);
		lenient().when(membershipRepository.findByRoomIdAndUserIdAndActiveTrue(9L, viewerUserId))
				.thenReturn(Optional.of(membership));
		lenient().when(teamMemberRepository.findAllBySessionIdOrderBySideAscLaneAsc(7L)).thenReturn(roster());

		List<Player> players = IntStream.rangeClosed(1, 10).mapToObj(index -> {
			Player player = mock(Player.class);
			when(player.getId()).thenReturn((long) index);
			when(player.getDisplayName()).thenReturn("선수 " + index);
			return player;
		}).toList();
		lenient().when(playerRepository.findAllById(org.mockito.ArgumentMatchers.any())).thenReturn(players);

		User blueCaptain = mock(User.class);
		when(blueCaptain.getId()).thenReturn(1L);
		when(blueCaptain.getDisplayName()).thenReturn("BLUE 팀장");
		User redCaptain = mock(User.class);
		when(redCaptain.getId()).thenReturn(2L);
		when(redCaptain.getDisplayName()).thenReturn("RED 팀장");
		lenient().when(userRepository.findAllById(org.mockito.ArgumentMatchers.any()))
				.thenReturn(List.of(blueCaptain, redCaptain));
	}

	private Champion champion(int championId) {
		Champion champion = mock(Champion.class);
		lenient().when(champion.getId()).thenReturn(championId);
		lenient().when(champion.getRiotId()).thenReturn("Champion" + championId);
		lenient().when(champion.getNameKo()).thenReturn("챔피언 " + championId);
		lenient().when(champion.getImageUrl()).thenReturn(null);
		return champion;
	}

	private List<SessionTeam> teams() {
		return List.of(
				SessionTeam.create(7L, TeamSide.BLUE, 1L, NOW),
				SessionTeam.create(7L, TeamSide.RED, 2L, NOW));
	}

	private List<SessionTeamMember> roster() {
		Lane[] lanes = Lane.values();
		return IntStream.range(0, 10)
				.mapToObj(index -> SessionTeamMember.create(
						7L,
						index < 5 ? TeamSide.BLUE : TeamSide.RED,
						(long) index + 1,
						lanes[index % 5],
						NOW))
				.toList();
	}
}
