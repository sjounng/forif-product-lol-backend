package com.scrim.lolscrim.domain.match;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

import com.scrim.lolscrim.domain.group.RoomMembership;
import com.scrim.lolscrim.domain.group.RoomMembershipRepository;
import com.scrim.lolscrim.domain.champion.Champion;
import com.scrim.lolscrim.domain.champion.ChampionRepository;
import com.scrim.lolscrim.domain.draft.DraftAction;
import com.scrim.lolscrim.domain.draft.DraftActionRepository;
import com.scrim.lolscrim.domain.draft.DraftActionType;
import com.scrim.lolscrim.domain.match.dto.MatchOverviewResponse;
import com.scrim.lolscrim.domain.match.dto.MatchResponse;
import com.scrim.lolscrim.domain.match.dto.MatchStartRequestResponse;
import com.scrim.lolscrim.domain.match.dto.MatchParticipantStatsRequest;
import com.scrim.lolscrim.domain.match.dto.ProposeMatchResultRequest;
import com.scrim.lolscrim.domain.session.FearlessMode;
import com.scrim.lolscrim.domain.session.Lane;
import com.scrim.lolscrim.domain.session.MatchFormat;
import com.scrim.lolscrim.domain.session.PlayerRepository;
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
import com.scrim.lolscrim.global.error.ErrorCode;

@ExtendWith(MockitoExtension.class)
class MatchServiceTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 1, 9, 0);

	@Mock
	private ScrimSessionRepository sessionRepository;
	@Mock
	private SessionTeamRepository teamRepository;
	@Mock
	private SessionTeamMemberRepository teamMemberRepository;
	@Mock
	private RoomMembershipRepository membershipRepository;
	@Mock
	private MatchStartRequestRepository startRequestRepository;
	@Mock
	private ScrimMatchRepository matchRepository;
	@Mock
	private MatchParticipantRepository participantRepository;
	@Mock
	private DraftRepository draftRepository;
	@Mock
	private DraftActionRepository draftActionRepository;
	@Mock
	private PlayerRepository playerRepository;
	@Mock
	private ChampionRepository championRepository;
	@Mock
	private UserRepository userRepository;

	private MatchService service;

	@BeforeEach
	void setUp() {
		Clock clock = Clock.fixed(
				Instant.parse("2026-08-01T00:00:00Z"),
				ZoneId.of("Asia/Seoul"));
		service = new MatchService(
				sessionRepository,
				teamRepository,
				teamMemberRepository,
				membershipRepository,
				startRequestRepository,
				matchRepository,
				participantRepository,
				draftRepository,
				draftActionRepository,
				playerRepository,
				championRepository,
				userRepository,
				clock);
	}

	@Test
	void captainRequestsNextMatchAfterSessionConfirmation() {
		ScrimSession session = confirmedSession(MatchFormat.BEST_OF_3);
		stubSessionForUpdate(session, 1L);
		when(matchRepository.findFirstBySessionIdOrderByGameNoDesc(7L)).thenReturn(Optional.empty());
		when(startRequestRepository.saveAndFlush(any(MatchStartRequest.class)))
				.thenAnswer(invocation -> {
					MatchStartRequest request = invocation.getArgument(0);
					ReflectionTestUtils.setField(request, "id", 40L);
					return request;
				});
		User proposer = mock(User.class);
		when(proposer.getId()).thenReturn(1L);
		when(proposer.getDisplayName()).thenReturn("BLUE 팀장");
		when(userRepository.findById(1L)).thenReturn(Optional.of(proposer));

		MatchStartRequestResponse response = service.requestStart(1L, 7L, TeamSide.RED);

		assertThat(response.id()).isEqualTo(40L);
		assertThat(response.gameNo()).isEqualTo(1);
		assertThat(response.status()).isEqualTo(MatchStartRequestStatus.PENDING);
		assertThat(response.blueTeamSide()).isEqualTo(TeamSide.RED);
		assertThat(response.blueTeamName()).isEqualTo("RED 팀");
		assertThat(response.redTeamName()).isEqualTo("BLUE 팀");
		assertThat(response.canCancel()).isTrue();
	}

	@Test
	void opposingCaptainAcceptanceCreatesMatchDraftAndTenParticipantsAtomically() {
		ScrimSession session = confirmedSession(MatchFormat.BEST_OF_3);
		stubSessionForUpdate(session, 2L);
		MatchStartRequest request = MatchStartRequest.propose(session, 1, 1L, TeamSide.RED, NOW);
		ReflectionTestUtils.setField(request, "id", 40L);
		when(startRequestRepository.findById(40L)).thenReturn(Optional.of(request));
		when(startRequestRepository.findByIdForUpdate(40L)).thenReturn(Optional.of(request));
		when(matchRepository.findFirstBySessionIdOrderByGameNoDesc(7L)).thenReturn(Optional.empty());
		when(teamMemberRepository.findAllBySessionIdOrderBySideAscLaneAsc(7L))
				.thenReturn(roster());
		when(matchRepository.saveAndFlush(any(ScrimMatch.class))).thenAnswer(invocation -> {
			ScrimMatch match = invocation.getArgument(0);
			ReflectionTestUtils.setField(match, "id", 50L);
			return match;
		});
		when(draftRepository.save(any(Draft.class))).thenAnswer(invocation -> {
			Draft draft = invocation.getArgument(0);
			ReflectionTestUtils.setField(draft, "id", 60L);
			return draft;
		});

		MatchResponse response = service.acceptStart(2L, 40L);

		assertThat(response.status()).isEqualTo(MatchStatus.DRAFTING);
		assertThat(response.blueTeamSide()).isEqualTo(TeamSide.RED);
		assertThat(response.blueTeamName()).isEqualTo("RED 팀");
		assertThat(response.redTeamName()).isEqualTo("BLUE 팀");
		assertThat(response.draftId()).isEqualTo(60L);
		assertThat(session.getStatus()).isEqualTo(SessionStatus.IN_PROGRESS);
		assertThat(request.getStatus()).isEqualTo(MatchStartRequestStatus.ACCEPTED);
		verify(participantRepository).saveAll(any());
	}

	@Test
	void sessionCannotStartMatchBeforeConfirmation() {
		ScrimSession session = proposedSession(MatchFormat.BEST_OF_3);
		stubSessionForUpdate(session, 1L);

		assertThatThrownBy(() -> service.requestStart(1L, 7L))
				.isInstanceOfSatisfying(ApiException.class, exception ->
						assertThat(exception.getCode()).isEqualTo(ErrorCode.MATCH_CREATION_DENIED));
	}

	@Test
	void resultConfirmationCompletesBestOfThreeAtSecondWin() {
		ScrimSession session = confirmedSession(MatchFormat.BEST_OF_3);
		session.startMatchFlow(NOW.minusHours(1));
		ReflectionTestUtils.setField(session, "gameCount", (byte) 1);
		stubSessionForUpdate(session, 2L);
		ScrimMatch match = liveMatch(2);
		match.proposeResult(1L, TeamSide.BLUE, null, NOW.minusMinutes(1));
		when(matchRepository.findById(50L)).thenReturn(Optional.of(match));
		when(matchRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(match));
		ScrimMatch previous = ScrimMatch.createDrafting(7L, 9L, 1, NOW.minusHours(2));
		ReflectionTestUtils.setField(previous, "id", 49L);
		previous.markReadyToPlay(NOW.minusHours(2).plusMinutes(5));
		previous.start(NOW.minusHours(2).plusMinutes(10));
		previous.proposeResult(1L, TeamSide.BLUE, null, NOW.minusHours(1).plusMinutes(30));
		previous.complete(NOW.minusHours(1));
		when(matchRepository.findAllBySessionIdOrderByGameNoAsc(7L)).thenReturn(List.of(previous, match));
		Draft draft = draft(50L);
		when(draftRepository.findByMatchId(50L)).thenReturn(Optional.of(draft));
		MatchParticipant blue = MatchParticipant.from(50L, 9L, roster().getFirst());
		when(participantRepository.findAllByMatchId(50L)).thenReturn(List.of(blue));

		MatchResponse response = service.acceptResult(2L, 50L);

		assertThat(response.status()).isEqualTo(MatchStatus.COMPLETED);
		assertThat(response.winnerSide()).isEqualTo(TeamSide.BLUE);
		assertThat(session.getStatus()).isEqualTo(SessionStatus.FINISHED);
		assertThat(session.getGameCount()).isEqualTo((byte) 2);
		assertThat(blue.getWin()).isTrue();
	}

	@Test
	void rejectedResultBecomesDisputedAndBlocksNextMatch() {
		ScrimSession session = confirmedSession(MatchFormat.BEST_OF_5);
		session.startMatchFlow(NOW.minusHours(1));
		stubSessionForUpdate(session, 2L);
		ScrimMatch match = liveMatch(1);
		match.proposeResult(1L, TeamSide.RED, null, NOW.minusMinutes(1));
		when(matchRepository.findById(50L)).thenReturn(Optional.of(match));
		when(matchRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(match));
		when(draftRepository.findByMatchId(50L)).thenReturn(Optional.of(draft(50L)));

		MatchResponse response = service.rejectResult(2L, 50L);

		assertThat(response.status()).isEqualTo(MatchStatus.RESULT_DISPUTED);
		assertThat(response.canProposeResult()).isTrue();
	}

	@Test
	void resultProposalRecordsEveryParticipantsKda() {
		ScrimSession session = confirmedSession(MatchFormat.BEST_OF_3);
		session.startMatchFlow(NOW.minusHours(1));
		stubSessionForUpdate(session, 1L);
		ScrimMatch match = liveMatch(1);
		when(matchRepository.findById(50L)).thenReturn(Optional.of(match));
		when(matchRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(match));
		when(draftRepository.findByMatchId(50L)).thenReturn(Optional.of(draft(50L)));
		List<MatchParticipant> participants = roster().stream()
				.map(member -> MatchParticipant.from(50L, 9L, member))
				.toList();
		when(participantRepository.findAllByMatchId(50L)).thenReturn(participants);
		List<MatchParticipantStatsRequest> stats = participants.stream()
				.map(participant -> new MatchParticipantStatsRequest(
						participant.getPlayerId(), 3, 2, 7))
				.toList();

		MatchResponse response = service.proposeResult(
				1L,
				50L,
				new ProposeMatchResultRequest(TeamSide.BLUE, null, stats));

		assertThat(response.status()).isEqualTo(MatchStatus.RESULT_PENDING);
		assertThat(participants).allSatisfy(participant -> {
			assertThat(participant.getKills()).isEqualTo(3);
			assertThat(participant.getDeaths()).isEqualTo(2);
			assertThat(participant.getAssists()).isEqualTo(7);
		});
	}

	@Test
	void overviewIncludesDraftActionsInStepOrder() {
		ScrimSession session = confirmedSession(MatchFormat.BEST_OF_3);
		when(sessionRepository.findById(7L)).thenReturn(Optional.of(session));
		when(membershipRepository.findByRoomIdAndUserIdAndActiveTrue(9L, 1L))
				.thenReturn(Optional.of(mock(RoomMembership.class)));
		when(teamRepository.findAllBySessionIdOrderBySideAsc(7L)).thenReturn(List.of(
				SessionTeam.create(7L, TeamSide.BLUE, 1L, NOW),
				SessionTeam.create(7L, TeamSide.RED, 2L, NOW)));
		ScrimMatch match = liveMatch(1);
		when(matchRepository.findAllBySessionIdOrderByGameNoAsc(7L)).thenReturn(List.of(match));
		Draft draft = draft(50L);
		when(draftRepository.findAllByMatchIdIn(List.of(50L))).thenReturn(List.of(draft));
		when(startRequestRepository.findFirstBySessionIdAndStatusOrderByCreatedAtDesc(
				7L, MatchStartRequestStatus.PENDING)).thenReturn(Optional.empty());

		DraftAction action = mock(DraftAction.class);
		when(action.getDraftId()).thenReturn(60L);
		when(action.getStepNo()).thenReturn((byte) 8);
		when(action.getSide()).thenReturn(TeamSide.RED);
		when(action.getActionType()).thenReturn(DraftActionType.PICK);
		when(action.getChampionId()).thenReturn(266);
		when(action.getPlayerId()).thenReturn(6L);
		when(draftActionRepository.findAllByDraftIdInOrderByDraftIdAscStepNoAsc(List.of(60L)))
				.thenReturn(List.of(action));
		Champion champion = mock(Champion.class);
		when(champion.getId()).thenReturn(266);
		when(champion.getRiotId()).thenReturn("Aatrox");
		when(champion.getNameKo()).thenReturn("아트록스");
		when(champion.getImageUrl()).thenReturn("https://example.com/Aatrox.png");
		when(championRepository.findAllById(any())).thenReturn(List.of(champion));

		MatchOverviewResponse response = service.getOverview(1L, 7L);

		assertThat(response.matches()).singleElement().satisfies(history -> {
			assertThat(history.draftActions()).singleElement().satisfies(actionHistory -> {
				assertThat(actionHistory.stepNo()).isEqualTo(8);
				assertThat(actionHistory.side()).isEqualTo(TeamSide.RED);
				assertThat(actionHistory.actionType()).isEqualTo(DraftActionType.PICK);
				assertThat(actionHistory.playerId()).isEqualTo(6L);
				assertThat(actionHistory.champion().riotId()).isEqualTo("Aatrox");
			});
		});
	}

	@Test
	void unlimitedSessionCreatorCanFinishBetweenMatches() {
		ScrimSession session = confirmedSession(MatchFormat.UNLIMITED);
		session.startMatchFlow(NOW.minusHours(1));
		stubSessionForUpdate(session, 1L);
		when(matchRepository.findAllBySessionIdOrderByGameNoAsc(7L)).thenReturn(List.of());
		when(startRequestRepository.findFirstBySessionIdAndStatusOrderByCreatedAtDesc(
				7L, MatchStartRequestStatus.PENDING)).thenReturn(Optional.empty());

		MatchOverviewResponse response = service.finishUnlimited(1L, 7L);

		assertThat(session.getStatus()).isEqualTo(SessionStatus.FINISHED);
		assertThat(response.canRequestStart()).isFalse();
	}

	private void stubSessionForUpdate(ScrimSession session, Long viewerUserId) {
		when(sessionRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(session));
		when(membershipRepository.findByRoomIdAndUserIdAndActiveTrue(9L, viewerUserId))
				.thenReturn(Optional.of(mock(RoomMembership.class)));
		when(teamRepository.findAllBySessionIdOrderBySideAsc(7L)).thenReturn(List.of(
				SessionTeam.create(7L, TeamSide.BLUE, 1L, NOW),
				SessionTeam.create(7L, TeamSide.RED, 2L, NOW)));
	}

	private ScrimSession proposedSession(MatchFormat format) {
		ScrimSession session = ScrimSession.propose(
				9L,
				1L,
				"정기 내전",
				format,
				FearlessMode.NONE,
				true,
				NOW.minusHours(2));
		ReflectionTestUtils.setField(session, "id", 7L);
		return session;
	}

	private ScrimSession confirmedSession(MatchFormat format) {
		ScrimSession session = proposedSession(format);
		session.confirm(NOW.minusHours(1));
		return session;
	}

	private ScrimMatch liveMatch(int gameNo) {
		ScrimMatch match = ScrimMatch.createDrafting(7L, 9L, gameNo, NOW.minusHours(1));
		ReflectionTestUtils.setField(match, "id", 50L);
		match.markReadyToPlay(NOW.minusMinutes(50));
		match.start(NOW.minusMinutes(45));
		return match;
	}

	private Draft draft(Long matchId) {
		Draft draft = Draft.create(matchId, 7L, NOW.minusHours(1));
		ReflectionTestUtils.setField(draft, "id", 60L);
		return draft;

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
