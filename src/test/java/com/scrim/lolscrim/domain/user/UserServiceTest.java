package com.scrim.lolscrim.domain.user;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.scrim.lolscrim.domain.auth.UserSessionRepository;
import com.scrim.lolscrim.domain.user.dto.ChangePasswordRequest;
import com.scrim.lolscrim.global.error.ApiException;
import com.scrim.lolscrim.domain.player.RiotProfileSyncService;
import com.scrim.lolscrim.domain.player.RiotAccountRepository;
import com.scrim.lolscrim.domain.player.RiotRankSnapshotRepository;
import com.scrim.lolscrim.domain.group.RoomMembershipRepository;
import com.scrim.lolscrim.domain.session.PlayerRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private UserSessionRepository userSessionRepository;

	@Mock
	private PasswordEncoder passwordEncoder;
	@Mock private RiotProfileSyncService riotProfileSyncService;
	@Mock private RiotAccountRepository riotAccountRepository;
	@Mock private RiotRankSnapshotRepository riotRankSnapshotRepository;
	@Mock private RoomMembershipRepository roomMembershipRepository;
	@Mock private PlayerRepository playerRepository;

	@Mock
	private User user;

	private UserService userService;

	@BeforeEach
	void setUp() {
		userService = new UserService(
				userRepository, userSessionRepository, passwordEncoder, riotProfileSyncService,
				riotAccountRepository, riotRankSnapshotRepository, roomMembershipRepository, playerRepository);
		when(userRepository.findById(1L)).thenReturn(Optional.of(user));
		when(user.getStatus()).thenReturn(UserStatus.ACTIVE);
		lenient().when(user.getPasswordHash()).thenReturn("old-hash");
	}

	@Test
	void changesPasswordAndRevokesAllSessions() {
		ChangePasswordRequest request = new ChangePasswordRequest("old-password", "new-password");
		when(passwordEncoder.matches("old-password", "old-hash")).thenReturn(true);
		when(passwordEncoder.matches("new-password", "old-hash")).thenReturn(false);
		when(passwordEncoder.encode("new-password")).thenReturn("new-hash");

		userService.changePassword(1L, request);

		verify(user).changePassword("new-hash");
		verify(userSessionRepository).revokeAllByUserId(
				org.mockito.ArgumentMatchers.eq(1L),
				org.mockito.ArgumentMatchers.any(LocalDateTime.class));
	}

	@Test
	void rejectsIncorrectCurrentPassword() {
		ChangePasswordRequest request = new ChangePasswordRequest("wrong-password", "new-password");
		when(passwordEncoder.matches("wrong-password", "old-hash")).thenReturn(false);

		assertThatThrownBy(() -> userService.changePassword(1L, request))
				.isInstanceOf(ApiException.class)
				.hasMessage("현재 비밀번호가 올바르지 않습니다.");

		verify(user, never()).changePassword(org.mockito.ArgumentMatchers.anyString());
		verify(userSessionRepository, never()).revokeAllByUserId(
				org.mockito.ArgumentMatchers.anyLong(),
				org.mockito.ArgumentMatchers.any(LocalDateTime.class));
	}

	@Test
	void rejectsSamePassword() {
		ChangePasswordRequest request = new ChangePasswordRequest("same-password", "same-password");
		when(passwordEncoder.matches("same-password", "old-hash")).thenReturn(true);

		assertThatThrownBy(() -> userService.changePassword(1L, request))
				.isInstanceOf(ApiException.class)
				.hasMessage("새 비밀번호는 현재 비밀번호와 달라야 합니다.");

		verify(user, never()).changePassword(org.mockito.ArgumentMatchers.anyString());
	}

	@Test
	void updatesOnlyDisplayNameWhenAvatarUrlAbsent() {
		when(user.getDisplayName()).thenReturn("기존이름");
		when(user.getAvatarUrl()).thenReturn("https://old.example.com/a.png");

		userService.updateProfile(1L, json("{\"displayName\":\"새이름\"}"));

		verify(user).updateProfile("새이름", "https://old.example.com/a.png");
	}

	@Test
	void clearsAvatarUrlWhenExplicitNull() {
		when(user.getDisplayName()).thenReturn("기존이름");

		userService.updateProfile(1L, json("{\"avatarUrl\":null}"));

		verify(user).updateProfile("기존이름", null);
	}

	@Test
	void rejectsBlankDisplayName() {
		assertThatThrownBy(() -> userService.updateProfile(1L, json("{\"displayName\":\"\"}")))
				.isInstanceOf(ApiException.class)
				.hasMessage("displayName: 공백일 수 없습니다.");

		verify(user, never()).updateProfile(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
	}

	@Test
	void rejectsNonHttpAvatarUrl() {
		when(user.getDisplayName()).thenReturn("기존이름");

		assertThatThrownBy(() -> userService.updateProfile(1L, json("{\"avatarUrl\":\"not-a-url\"}")))
				.isInstanceOf(ApiException.class)
				.hasMessage("avatarUrl: 올바른 http(s) URL 형식이 아닙니다.");
	}

	@Test
	void rejectsProfileUpdateForInactiveAccount() {
		when(user.getStatus()).thenReturn(UserStatus.SUSPENDED);

		assertThatThrownBy(() -> userService.updateProfile(1L, json("{\"displayName\":\"새이름\"}")))
				.isInstanceOf(ApiException.class)
				.hasMessage("이용할 수 없는 계정입니다.");
	}

	private static JsonNode json(String json) {
		try {
			return new ObjectMapper().readTree(json);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
