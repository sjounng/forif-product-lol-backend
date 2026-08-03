package com.scrim.lolscrim.domain.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.scrim.lolscrim.domain.group.GuestSessionRepository;
import com.scrim.lolscrim.domain.room.Room;
import com.scrim.lolscrim.domain.group.RoomMembership;
import com.scrim.lolscrim.domain.group.RoomMembershipRepository;
import com.scrim.lolscrim.domain.player.Lane;
import com.scrim.lolscrim.domain.player.PlayerRepository;
import com.scrim.lolscrim.domain.player.Player;
import com.scrim.lolscrim.domain.room.RoomRepository;
import com.scrim.lolscrim.domain.session.dto.CreateSessionRequest;
import com.scrim.lolscrim.domain.session.dto.SessionResponse;
import com.scrim.lolscrim.domain.session.dto.SessionRosterMemberRequest;
import com.scrim.lolscrim.domain.user.User;
import com.scrim.lolscrim.domain.user.UserRepository;
import com.scrim.lolscrim.global.error.ApiException;
import com.scrim.lolscrim.global.error.ErrorCode;
import com.scrim.lolscrim.domain.riot.RiotAccountRepository;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 31, 12, 0);

	@Mock
	private ScrimSessionRepository sessionRepository;
	@Mock
	private SessionTeamRepository teamRepository;
	@Mock
	private SessionTeamMemberRepository teamMemberRepository;
	@Mock
	private PlayerRepository playerRepository;
	@Mock
	private RoomRepository roomRepository;
	@Mock
	private RoomMembershipRepository membershipRepository;
	@Mock
	private GuestSessionRepository guestRepository;
	@Mock
	private UserRepository userRepository;
	@Mock
	private RiotAccountRepository riotAccountRepository;

	private SessionService sessionService;

	@BeforeEach
	void setUp() {
		Clock clock = Clock.fixed(
				Instant.parse("2026-07-31T03:00:00Z"),
				ZoneId.of("Asia/Seoul"));
		sessionService = new SessionService(
				sessionRepository,
				teamRepository,
				teamMemberRepository,
				playerRepository,
				roomRepository,
				membershipRepository,
				guestRepository,
				userRepository,
				riotAccountRepository,
				clock);
	}

	@Test
	void createsProposedSessionWithTwoFixedFivePlayerTeams() {
		Room room = room(1L, 2L);
		RoomMembership membership = mock(RoomMembership.class);
		when(roomRepository.findById(7L)).thenReturn(Optional.of(room));
		when(membershipRepository.findByRoomIdAndUserIdAndActiveTrue(anyLong(), anyLong()))
				.thenReturn(Optional.of(membership));
		when(sessionRepository.existsByRoomIdAndStatusIn(anyLong(), any())).thenReturn(false);
		when(sessionRepository.saveAndFlush(any(ScrimSession.class))).thenAnswer(invocation -> {
			ScrimSession session = invocation.getArgument(0);
			ReflectionTestUtils.setField(session, "id", 20L);
			return session;
		});
		when(teamRepository.save(any(SessionTeam.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(teamMemberRepository.save(any(SessionTeamMember.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));
		when(teamRepository.findAllBySessionIdOrderBySideAsc(20L)).thenReturn(List.of());
		when(teamMemberRepository.findAllBySessionIdOrderBySideAscLaneAsc(20L)).thenReturn(List.of());
		when(playerRepository.findAllById(any())).thenReturn(List.of());
		when(playerRepository.findByRoomIdAndMemberUserId(anyLong(), anyLong()))
				.thenReturn(Optional.empty());
		AtomicLong playerIds = new AtomicLong(100);
		when(playerRepository.save(any(Player.class))).thenAnswer(invocation -> {
			Player player = invocation.getArgument(0);
			ReflectionTestUtils.setField(player, "id", playerIds.incrementAndGet());
			return player;
		});
		when(userRepository.findById(anyLong())).thenAnswer(invocation -> {
			Long id = invocation.getArgument(0);
			User user = mock(User.class);
			when(user.getDisplayName()).thenReturn("참가자" + id);
			return Optional.of(user);
		});

		SessionResponse response = sessionService.createSession(
				1L,
				7L,
				validRequest(MatchFormat.BEST_OF_5, FearlessMode.HARD_FEARLESS));

		assertThat(response.status()).isEqualTo(SessionStatus.PROPOSED);
		assertThat(response.matchFormat()).isEqualTo(MatchFormat.BEST_OF_5);
		assertThat(response.fearlessMode()).isEqualTo(FearlessMode.HARD_FEARLESS);
		verify(teamRepository, times(2)).save(any(SessionTeam.class));
		verify(teamMemberRepository, times(10)).save(any(SessionTeamMember.class));
	}

	@Test
	void createsSessionWithRegisteredRiotPlayers() {
		Room room = room(1L, 2L);
		when(roomRepository.findById(7L)).thenReturn(Optional.of(room));
		when(membershipRepository.findByRoomIdAndUserIdAndActiveTrue(anyLong(), anyLong()))
				.thenReturn(Optional.of(mock(RoomMembership.class)));
		when(sessionRepository.existsByRoomIdAndStatusIn(anyLong(), any())).thenReturn(false);
		when(sessionRepository.saveAndFlush(any(ScrimSession.class))).thenAnswer(invocation -> {
			ScrimSession session = invocation.getArgument(0);
			ReflectionTestUtils.setField(session, "id", 30L);
			return session;
		});
		when(teamRepository.save(any(SessionTeam.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(teamMemberRepository.save(any(SessionTeamMember.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));
		when(teamRepository.findAllBySessionIdOrderBySideAsc(30L)).thenReturn(List.of());
		when(teamMemberRepository.findAllBySessionIdOrderBySideAscLaneAsc(30L)).thenReturn(List.of());
		when(playerRepository.findAllById(any())).thenReturn(List.of());
		User blueCaptain = user("블루 팀장");
		User redCaptain = user("레드 팀장");
		when(userRepository.findById(1L)).thenReturn(Optional.of(blueCaptain));
		when(userRepository.findById(2L)).thenReturn(Optional.of(redCaptain));
		when(playerRepository.findById(anyLong())).thenAnswer(invocation -> {
			Long playerId = invocation.getArgument(0);
			Player player = Player.fromRiotAccount(
					7L,
					1000L + playerId,
					"Riot player " + playerId,
					1L,
					NOW);
			ReflectionTestUtils.setField(player, "id", playerId);
			return Optional.of(player);
		});

		SessionResponse response = sessionService.createSession(
				1L,
				7L,
				new CreateSessionRequest(
						"Riot ID 내전",
						MatchFormat.BEST_OF_3,
						FearlessMode.NONE,
						true,
						TeamSide.BLUE,
						2L,
						playerTeam(1),
						playerTeam(6)));

		assertThat(response.status()).isEqualTo(SessionStatus.PROPOSED);
		verify(teamMemberRepository, times(10)).save(any(SessionTeamMember.class));
	}

	@Test
	void rejectsDuplicateLaneAssignments() {
		Room room = room(1L, 2L);
		when(roomRepository.findById(7L)).thenReturn(Optional.of(room));
		when(membershipRepository.findByRoomIdAndUserIdAndActiveTrue(7L, 1L))
				.thenReturn(Optional.of(mock(RoomMembership.class)));
		when(membershipRepository.findByRoomIdAndUserIdAndActiveTrue(7L, 2L))
				.thenReturn(Optional.of(mock(RoomMembership.class)));
		when(sessionRepository.existsByRoomIdAndStatusIn(anyLong(), any())).thenReturn(false);
		List<SessionRosterMemberRequest> invalidBlue = List.of(
				member(1, Lane.TOP), member(2, Lane.TOP), member(3, Lane.MID),
				member(4, Lane.ADC), member(5, Lane.SUPPORT));
		CreateSessionRequest request = new CreateSessionRequest(
				"세션",
				MatchFormat.BEST_OF_3,
				FearlessMode.NONE,
				true,
				TeamSide.BLUE,
				2L,
				invalidBlue,
				team(6));

		assertThatThrownBy(() -> sessionService.createSession(1L, 7L, request))
				.isInstanceOfSatisfying(ApiException.class, exception ->
						assertThat(exception.getCode()).isEqualTo(ErrorCode.SESSION_INVALID_ROSTER));
	}

	@Test
	void blocksSecondActiveSessionInSameGroup() {
		Room room = room(1L, 2L);
		when(roomRepository.findById(7L)).thenReturn(Optional.of(room));
		when(membershipRepository.findByRoomIdAndUserIdAndActiveTrue(7L, 1L))
				.thenReturn(Optional.of(mock(RoomMembership.class)));
		when(membershipRepository.findByRoomIdAndUserIdAndActiveTrue(7L, 2L))
				.thenReturn(Optional.of(mock(RoomMembership.class)));
		when(sessionRepository.existsByRoomIdAndStatusIn(anyLong(), any())).thenReturn(true);

		assertThatThrownBy(() -> sessionService.createSession(
				1L, 7L, validRequest(MatchFormat.UNLIMITED, FearlessMode.GLOBAL_FEARLESS)))
				.isInstanceOfSatisfying(ApiException.class, exception ->
						assertThat(exception.getCode()).isEqualTo(ErrorCode.SESSION_ACTIVE_EXISTS));
	}

	@Test
	void onlyGroupCaptainsCanCreateSessions() {
		Room room = room(1L, 2L);
		when(roomRepository.findById(7L)).thenReturn(Optional.of(room));
		when(membershipRepository.findByRoomIdAndUserIdAndActiveTrue(7L, 3L))
				.thenReturn(Optional.of(mock(RoomMembership.class)));

		assertThatThrownBy(() -> sessionService.createSession(
				3L, 7L, validRequest(MatchFormat.BEST_OF_3, FearlessMode.NONE)))
				.isInstanceOfSatisfying(ApiException.class, exception ->
						assertThat(exception.getCode()).isEqualTo(ErrorCode.SESSION_CREATION_DENIED));
	}

	@Test
	void opponentCaptainCanAcceptProposal() {
		ScrimSession session = mock(ScrimSession.class);
		SessionTeam blue = mock(SessionTeam.class);
		SessionTeam red = mock(SessionTeam.class);
		when(sessionRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(session));
		when(session.getRoomId()).thenReturn(7L);
		when(session.getCreatedByUserId()).thenReturn(1L);
		when(session.getStatus()).thenReturn(SessionStatus.PROPOSED);
		when(session.getId()).thenReturn(20L);
		when(membershipRepository.findByRoomIdAndUserIdAndActiveTrue(7L, 2L))
				.thenReturn(Optional.of(mock(RoomMembership.class)));
		when(blue.getSide()).thenReturn(TeamSide.BLUE);
		when(blue.getCaptainUserId()).thenReturn(1L);
		when(red.getSide()).thenReturn(TeamSide.RED);
		when(red.getCaptainUserId()).thenReturn(2L);
		when(teamRepository.findAllBySessionIdOrderBySideAsc(20L)).thenReturn(List.of(blue, red));
		when(teamMemberRepository.findAllBySessionIdOrderBySideAscLaneAsc(20L)).thenReturn(List.of());
		when(playerRepository.findAllById(any())).thenReturn(List.of());
		when(userRepository.findById(anyLong())).thenAnswer(invocation -> {
			Long id = invocation.getArgument(0);
			User user = mock(User.class);
			when(user.getId()).thenReturn(id);
			when(user.getDisplayName()).thenReturn("팀장" + id);
			return Optional.of(user);
		});

		sessionService.accept(2L, 20L);

		verify(session).confirm(NOW);
	}

	@Test
	void captainCanRenameOnlyTheirSessionTeam() {
		ScrimSession session = ScrimSession.propose(
				7L, 1L, "정기 내전", MatchFormat.BEST_OF_3, FearlessMode.NONE, true, NOW);
		ReflectionTestUtils.setField(session, "id", 20L);
		SessionTeam blue = SessionTeam.create(20L, TeamSide.BLUE, 1L, "고양이 팀", NOW);
		SessionTeam red = SessionTeam.create(20L, TeamSide.RED, 2L, "강아지 팀", NOW);
		when(sessionRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(session));
		when(membershipRepository.findByRoomIdAndUserIdAndActiveTrue(7L, 2L))
				.thenReturn(Optional.of(mock(RoomMembership.class)));
		when(teamRepository.findAllBySessionIdOrderBySideAsc(20L)).thenReturn(List.of(blue, red));
		when(teamMemberRepository.findAllBySessionIdOrderBySideAscLaneAsc(20L)).thenReturn(List.of());
		when(playerRepository.findAllById(any())).thenReturn(List.of());
		when(userRepository.findById(anyLong())).thenAnswer(invocation -> {
			Long id = invocation.getArgument(0);
			User user = mock(User.class);
			when(user.getId()).thenReturn(id);
			when(user.getDisplayName()).thenReturn("팀장" + id);
			return Optional.of(user);
		});

		SessionResponse response = sessionService.renameTeam(2L, 20L, "  새 이름  ");

		assertThat(red.getTeamName()).isEqualTo("새 이름");
		assertThat(response.teams()).extracting(team -> team.teamName())
				.containsExactly("고양이 팀", "새 이름");
	}

	private Room room(Long ownerId, Long opponentId) {
		return mock(Room.class);
	}

	private User user(String displayName) {
		User user = mock(User.class);
		when(user.getDisplayName()).thenReturn(displayName);
		return user;
	}

	private CreateSessionRequest validRequest(MatchFormat format, FearlessMode mode) {
		return new CreateSessionRequest(
				"정기 내전",
				format,
				mode,
				true,
				TeamSide.BLUE,
				2L,
				team(1),
				team(6));
	}

	private List<SessionRosterMemberRequest> team(long firstId) {
		Lane[] lanes = Lane.values();
		return IntStream.range(0, 5)
				.mapToObj(index -> member(firstId + index, lanes[index]))
				.toList();
	}

	private List<SessionRosterMemberRequest> playerTeam(long firstId) {
		Lane[] lanes = Lane.values();
		return IntStream.range(0, 5)
				.mapToObj(index -> new SessionRosterMemberRequest(
						ParticipantType.PLAYER,
						firstId + index,
						lanes[index]))
				.toList();
	}

	private SessionRosterMemberRequest member(long id, Lane lane) {
		return new SessionRosterMemberRequest(ParticipantType.MEMBER, id, lane);
	}
}
