package com.scrim.lolscrim.domain.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.scrim.lolscrim.domain.group.dto.CreateRoomRequest;
import com.scrim.lolscrim.domain.group.dto.RoomResponse;
import com.scrim.lolscrim.domain.user.User;
import com.scrim.lolscrim.domain.user.UserRepository;
import com.scrim.lolscrim.domain.user.UserStatus;
import com.scrim.lolscrim.domain.session.PlayerRepository;
import com.scrim.lolscrim.domain.player.RiotAccountRepository;
import com.scrim.lolscrim.domain.player.RiotRankSnapshotRepository;
import com.scrim.lolscrim.domain.player.PlayerRatingRepository;
import com.scrim.lolscrim.global.error.ApiException;
import com.scrim.lolscrim.global.error.ErrorCode;

@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

	@Mock
	private RoomRepository roomRepository;
	@Mock
	private RoomMembershipRepository membershipRepository;
	@Mock
	private RoomCaptainInvitationRepository invitationRepository;
	@Mock
	private GuestSessionRepository guestSessionRepository;
	@Mock
	private UserRepository userRepository;
	@Mock
	private PasswordEncoder passwordEncoder;
	@Mock
	private PlayerRepository playerRepository;
	@Mock
	private RiotAccountRepository riotAccountRepository;
	@Mock
	private RiotRankSnapshotRepository riotRankSnapshotRepository;
	@Mock
	private PlayerRatingRepository playerRatingRepository;
	@Mock
	private User owner;
	@Mock
	private User invitee;
	@Mock
	private RoomMembership ownerMembership;

	private RoomService roomService;

	@BeforeEach
	void setUp() {
		roomService = new RoomService(
				roomRepository,
				membershipRepository,
				invitationRepository,
				guestSessionRepository,
				userRepository,
				passwordEncoder,
				playerRepository,
				riotAccountRepository,
				riotRankSnapshotRepository,
				playerRatingRepository);
	}

	@Test
	void createsGroupOwnerMembershipAndPendingCaptainInvitation() {
		when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
		when(userRepository.findById(2L)).thenReturn(Optional.of(invitee));
		when(owner.getId()).thenReturn(1L);
		when(owner.getStatus()).thenReturn(UserStatus.ACTIVE);
		when(owner.getDisplayName()).thenReturn("소유자");
		when(invitee.getId()).thenReturn(2L);
		when(invitee.getStatus()).thenReturn(UserStatus.ACTIVE);
		when(passwordEncoder.encode("pass")).thenReturn("encoded");
		when(roomRepository.save(any(Room.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(membershipRepository.save(any(RoomMembership.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));
		when(invitationRepository.save(any(RoomCaptainInvitation.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));
		when(membershipRepository.findByRoomIdAndUserIdAndActiveTrue(null, 1L))
				.thenReturn(Optional.of(ownerMembership));
		when(ownerMembership.getRole()).thenReturn(GroupRole.GROUP_OWNER);

		RoomResponse response = roomService.createRoom(
				1L,
				new CreateRoomRequest("정기 내전", "설명", 2L, true, "pass"));

		assertThat(response.name()).isEqualTo("정기 내전");
		assertThat(response.publicCode()).hasSize(8);
		assertThat(response.entryPasswordProtected()).isTrue();
		assertThat(response.captainInvitationStatus()).isEqualTo(CaptainInvitationStatus.PENDING);
		assertThat(response.myRole()).isEqualTo(GroupRole.GROUP_OWNER);
		verify(membershipRepository).save(any(RoomMembership.class));
		verify(invitationRepository).save(any(RoomCaptainInvitation.class));
	}

	@Test
	void rejectsOwnerAsOpponentCaptain() {
		when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
		when(owner.getId()).thenReturn(1L);
		when(owner.getStatus()).thenReturn(UserStatus.ACTIVE);

		assertThatThrownBy(() -> roomService.createRoom(
				1L,
				new CreateRoomRequest("그룹", null, 1L, true, null)))
				.isInstanceOfSatisfying(ApiException.class, exception ->
						assertThat(exception.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
	}

	@Test
	void deniesRoomDetailsToNonMember() {
		Room room = org.mockito.Mockito.mock(Room.class);
		when(roomRepository.findById(7L)).thenReturn(Optional.of(room));
		when(room.getStatus()).thenReturn(RoomStatus.ACTIVE);
		when(membershipRepository.findByRoomIdAndUserIdAndActiveTrue(7L, 99L))
				.thenReturn(Optional.empty());

		assertThatThrownBy(() -> roomService.getRoom(99L, 7L))
				.isInstanceOfSatisfying(ApiException.class, exception ->
						assertThat(exception.getCode()).isEqualTo(ErrorCode.ROOM_ACCESS_DENIED));
	}
}
