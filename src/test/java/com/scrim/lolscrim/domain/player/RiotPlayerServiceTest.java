package com.scrim.lolscrim.domain.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.scrim.lolscrim.domain.group.GroupRole;
import com.scrim.lolscrim.domain.group.RoomMembership;
import com.scrim.lolscrim.domain.group.RoomMembershipRepository;
import com.scrim.lolscrim.domain.room.RoomRepository;
import com.scrim.lolscrim.domain.player.RiotProfileSyncService.SyncedRiotProfile;
import com.scrim.lolscrim.domain.player.dto.AddRiotPlayerRequest;
import com.scrim.lolscrim.domain.player.dto.RiotPlayerResponse;
import com.scrim.lolscrim.domain.player.Player;
import com.scrim.lolscrim.domain.player.PlayerRepository;
import com.scrim.lolscrim.domain.riot.QueueType;
import com.scrim.lolscrim.domain.riot.RankDivision;
import com.scrim.lolscrim.domain.riot.RiotAccount;
import com.scrim.lolscrim.domain.riot.RiotAccountRepository;
import com.scrim.lolscrim.domain.riot.RiotPlatform;
import com.scrim.lolscrim.domain.riot.RiotRankSnapshot;
import com.scrim.lolscrim.domain.riot.RiotRankSnapshotRepository;
import com.scrim.lolscrim.domain.riot.Tier;
import com.scrim.lolscrim.global.error.ApiException;
import com.scrim.lolscrim.global.error.ErrorCode;

@ExtendWith(MockitoExtension.class)
class RiotPlayerServiceTest {

	@Mock
	private RiotProfileSyncService profileSyncService;
	@Mock
	private RiotAccountRepository riotAccountRepository;
	@Mock
	private PlayerRepository playerRepository;
	@Mock
	private PlayerRatingRepository ratingRepository;
	@Mock
	private PlayerLaneRatingRepository laneRatingRepository;
	@Mock
	private RiotRankSnapshotRepository rankRepository;
	@Mock
	private RoomRepository roomRepository;
	@Mock
	private RoomMembershipRepository membershipRepository;
	@Mock
	private RoomMembership membership;

	private RiotPlayerService service;

	@BeforeEach
	void setUp() {
		Clock clock = Clock.fixed(
				Instant.parse("2026-07-31T15:00:00Z"),
				ZoneId.of("Asia/Seoul"));
		service = new RiotPlayerService(
				profileSyncService,
				riotAccountRepository,
				playerRepository,
				ratingRepository,
				laneRatingRepository,
				rankRepository,
				roomRepository,
				membershipRepository,
				clock);
	}

	@Test
	void managerAddsVerifiedRiotAccountAsActivePlayer() {
		when(roomRepository.existsById(7L)).thenReturn(true);
		when(membershipRepository.findByRoomIdAndUserIdAndActiveTrue(7L, 1L))
				.thenReturn(Optional.of(membership));
		when(membership.getRole()).thenReturn(GroupRole.GROUP_MANAGER);
		RiotAccount account = RiotAccount.create("puuid-1", RiotPlatform.KR, "Hide on bush", "KR1");
		ReflectionTestUtils.setField(account, "id", 11L);
		RiotRankSnapshot rank = RiotRankSnapshot.create(
				11L, QueueType.RANKED_SOLO_5x5, Tier.DIAMOND, RankDivision.II, 42, 10, 5, 2642);
		when(profileSyncService.sync("Hide on bush", "KR1")).thenReturn(new SyncedRiotProfile(
				account, rank, java.util.Map.of(Lane.MID, 5, Lane.TOP, 3)));
		when(playerRepository.findByRoomIdAndRiotAccountId(7L, 11L))
				.thenReturn(Optional.empty());
		when(playerRepository.save(org.mockito.ArgumentMatchers.any(Player.class)))
				.thenAnswer(invocation -> {
					Player player = invocation.getArgument(0);
					ReflectionTestUtils.setField(player, "id", 21L);
					return player;
				});
		when(ratingRepository.findById(21L)).thenReturn(Optional.empty());
		when(ratingRepository.save(org.mockito.ArgumentMatchers.any(PlayerRating.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));
		when(laneRatingRepository.saveAll(org.mockito.ArgumentMatchers.<PlayerLaneRating>anyList()))
				.thenAnswer(invocation -> invocation.getArgument(0));

		RiotPlayerResponse response = service.addPlayer(
				1L,
				7L,
				new AddRiotPlayerRequest(" Hide on bush ", " KR1 "));

		assertThat(response.id()).isEqualTo(21L);
		assertThat(response.displayName()).isEqualTo("Hide on bush");
		assertThat(response.riotAccount().gameName()).isEqualTo("Hide on bush");
		assertThat(response.riotAccount().tagLine()).isEqualTo("KR1");
		assertThat(response.rating()).isEqualTo(2742);
		assertThat(response.isActive()).isTrue();
	}

	@Test
	void regularMemberCannotCallRiotApiToAddPlayer() {
		when(roomRepository.existsById(7L)).thenReturn(true);
		when(membershipRepository.findByRoomIdAndUserIdAndActiveTrue(7L, 3L))
				.thenReturn(Optional.of(membership));
		when(membership.getRole()).thenReturn(GroupRole.GROUP_MEMBER);

		assertThatThrownBy(() -> service.addPlayer(
				3L,
				7L,
				new AddRiotPlayerRequest("Player One", "KR1")))
				.isInstanceOfSatisfying(ApiException.class, exception ->
						assertThat(exception.getCode()).isEqualTo(ErrorCode.ROOM_MANAGEMENT_DENIED));
		verify(profileSyncService, never()).sync("Player One", "KR1");
	}

	@Test
	void rejectsRiotAccountAlreadyActiveInRoom() {
		when(roomRepository.existsById(7L)).thenReturn(true);
		when(membershipRepository.findByRoomIdAndUserIdAndActiveTrue(7L, 1L))
				.thenReturn(Optional.of(membership));
		when(membership.getRole()).thenReturn(GroupRole.GROUP_OWNER);
		RiotAccount account = RiotAccount.create("puuid-1", RiotPlatform.KR, "Player One", "KR1");
		ReflectionTestUtils.setField(account, "id", 11L);
		when(profileSyncService.sync("Player One", "KR1")).thenReturn(new SyncedRiotProfile(
				account,
				RiotRankSnapshot.create(
						11L, QueueType.RANKED_SOLO_5x5, Tier.UNRANKED, null, 0, 0, 0, 0),
				java.util.Map.of()));
		Player player = Player.fromRiotAccount(
				7L,
				11L,
				"Player One",
				1L,
				java.time.LocalDateTime.of(2026, 8, 1, 0, 0));
		when(playerRepository.findByRoomIdAndRiotAccountId(7L, 11L))
				.thenReturn(Optional.of(player));

		assertThatThrownBy(() -> service.addPlayer(
				1L,
				7L,
				new AddRiotPlayerRequest("Player One", "KR1")))
				.isInstanceOfSatisfying(ApiException.class, exception ->
						assertThat(exception.getCode()).isEqualTo(ErrorCode.PLAYER_ALREADY_EXISTS));
	}

	@Test
	void managerRefreshesEveryActiveRiotAccountInRoom() {
		when(roomRepository.existsById(7L)).thenReturn(true);
		when(membershipRepository.findByRoomIdAndUserIdAndActiveTrue(7L, 1L))
				.thenReturn(Optional.of(membership));
		when(membership.getRole()).thenReturn(GroupRole.GROUP_MANAGER);
		Player first = Player.fromRiotAccount(
				7L, 11L, "Player One", 1L, java.time.LocalDateTime.of(2026, 8, 1, 0, 0));
		Player second = Player.fromRiotAccount(
				7L, 12L, "Player Two", 1L, java.time.LocalDateTime.of(2026, 8, 1, 0, 0));
		when(playerRepository.findAllByRoomIdAndRiotAccountIdIsNotNullAndActiveTrueOrderByCreatedAtAsc(7L))
				.thenReturn(List.of(first, second), List.of());

		List<RiotPlayerResponse> refreshed = service.syncPlayers(1L, 7L);

		assertThat(refreshed).isEmpty();
		verify(profileSyncService).syncRank(11L);
		verify(profileSyncService).syncRank(12L);
	}
}
