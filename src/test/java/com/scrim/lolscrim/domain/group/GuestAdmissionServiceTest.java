package com.scrim.lolscrim.domain.group;

import com.scrim.lolscrim.domain.room.Room;
import com.scrim.lolscrim.domain.room.RoomRepository;
import com.scrim.lolscrim.domain.room.RoomStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.scrim.lolscrim.domain.group.dto.GuestEntryRequest;
import com.scrim.lolscrim.domain.group.dto.PublicRoomResponse;
import com.scrim.lolscrim.global.error.ApiException;
import com.scrim.lolscrim.global.error.ErrorCode;
import com.scrim.lolscrim.domain.player.PlayerRepository;

@ExtendWith(MockitoExtension.class)
class GuestAdmissionServiceTest {

	@Mock
	private RoomRepository roomRepository;
	@Mock
	private GuestSessionRepository guestSessionRepository;
	@Mock
	private PasswordEncoder passwordEncoder;
	@Mock
	private RoomService roomService;
	@Mock
	private Room room;
	@Mock
	private PlayerRepository playerRepository;

	private GuestAdmissionService service;

	@BeforeEach
	void setUp() {
		service = new GuestAdmissionService(
				roomRepository,
				guestSessionRepository,
				passwordEncoder,
				roomService,
				playerRepository);
	}

	@Test
	void admitsGuestAndReturnsOnlyRawCookieToken() {
		when(roomRepository.findByPublicCodeAndStatus("ABCDEFGH", RoomStatus.ACTIVE))
				.thenReturn(Optional.of(room));
		when(room.getId()).thenReturn(1L);
		when(room.isGuestAdmissionEnabled()).thenReturn(true);
		when(guestSessionRepository.save(any(GuestSession.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));
		when(roomService.toPublicResponse(
				org.mockito.ArgumentMatchers.eq(room),
				org.mockito.ArgumentMatchers.any()))
				.thenReturn(new PublicRoomResponse(1L, "그룹", null, "ABCDEFGH", true, false, 1));

		var result = service.enter(
				"ABCDEFGH",
				new GuestEntryRequest("관전자", null),
				null,
				"127.0.0.1");

		assertThat(result.token()).hasSize(64);
		assertThat(result.response().guest().nickname()).isEqualTo("관전자");
	}

	@Test
	void rejectsWrongEntryPassword() {
		when(roomRepository.findByPublicCodeAndStatus("ABCDEFGH", RoomStatus.ACTIVE))
				.thenReturn(Optional.of(room));
		when(room.isGuestAdmissionEnabled()).thenReturn(true);
		when(room.getEntryCodeHash()).thenReturn("encoded");
		when(passwordEncoder.matches("wrong", "encoded")).thenReturn(false);

		assertThatThrownBy(() -> service.enter(
				"ABCDEFGH",
				new GuestEntryRequest("관전자", "wrong"),
				null,
				"127.0.0.1"))
				.isInstanceOfSatisfying(ApiException.class, exception ->
						assertThat(exception.getCode())
								.isEqualTo(ErrorCode.GUEST_ENTRY_PASSWORD_INVALID));
	}
}
