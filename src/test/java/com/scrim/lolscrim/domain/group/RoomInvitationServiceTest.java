package com.scrim.lolscrim.domain.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.scrim.lolscrim.domain.user.User;
import com.scrim.lolscrim.domain.user.UserRepository;
import com.scrim.lolscrim.global.error.ApiException;
import com.scrim.lolscrim.global.error.ErrorCode;

@ExtendWith(MockitoExtension.class)
class RoomInvitationServiceTest {

	@Mock
	private RoomRepository roomRepository;
	@Mock
	private RoomMembershipRepository membershipRepository;
	@Mock
	private RoomCaptainInvitationRepository invitationRepository;
	@Mock
	private UserRepository userRepository;
	@Mock
	private RoomService roomService;
	@Mock
	private RoomCaptainInvitation invitation;
	@Mock
	private Room room;
	@Mock
	private RoomMembership membership;
	@Mock
	private User inviter;
	@Mock
	private User invitee;

	private RoomInvitationService service;

	@BeforeEach
	void setUp() {
		service = new RoomInvitationService(
				roomRepository,
				membershipRepository,
				invitationRepository,
				userRepository,
				roomService);
	}

	@Test
	void acceptsPendingInvitationAndAssignsOpponentCaptain() {
		LocalDateTime future = LocalDateTime.now().plusDays(1);
		when(invitationRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(invitation));
		when(invitation.getInviteeUserId()).thenReturn(2L);
		when(invitation.getStatus()).thenReturn(CaptainInvitationStatus.PENDING);
		when(invitation.getExpiresAt()).thenReturn(future);
		when(invitation.isExpired(org.mockito.ArgumentMatchers.any(LocalDateTime.class))).thenReturn(false);
		when(invitation.getRoomId()).thenReturn(10L);
		when(invitation.getInvitedByUserId()).thenReturn(1L);
		when(roomService.requireRoom(10L)).thenReturn(room);
		when(room.getId()).thenReturn(10L);
		when(room.getOpponentCaptainUserId()).thenReturn(null);
		when(membershipRepository.findByRoomIdAndUserId(10L, 2L))
				.thenReturn(Optional.of(membership));
		when(userRepository.findById(1L)).thenReturn(Optional.of(inviter));
		when(userRepository.findById(2L)).thenReturn(Optional.of(invitee));
		when(inviter.getId()).thenReturn(1L);
		when(invitee.getId()).thenReturn(2L);

		var response = service.accept(2L, 5L);

		assertThat(response.status()).isEqualTo(CaptainInvitationStatus.PENDING);
		verify(membership).activate(
				org.mockito.ArgumentMatchers.eq(GroupRole.GROUP_MEMBER),
				org.mockito.ArgumentMatchers.any(LocalDateTime.class));
		verify(room).assignOpponentCaptain(
				org.mockito.ArgumentMatchers.eq(2L),
				org.mockito.ArgumentMatchers.any(LocalDateTime.class));
		verify(invitation).accept(org.mockito.ArgumentMatchers.any(LocalDateTime.class));
	}

	@Test
	void preventsAnotherUserFromResponding() {
		when(invitationRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(invitation));
		when(invitation.getInviteeUserId()).thenReturn(2L);

		assertThatThrownBy(() -> service.accept(3L, 5L))
				.isInstanceOfSatisfying(ApiException.class, exception ->
						assertThat(exception.getCode()).isEqualTo(ErrorCode.INVITATION_ACCESS_DENIED));
	}
}
