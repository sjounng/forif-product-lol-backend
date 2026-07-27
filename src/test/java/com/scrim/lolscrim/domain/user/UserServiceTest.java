package com.scrim.lolscrim.domain.user;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private UserSessionRepository userSessionRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Mock
	private User user;

	private UserService userService;

	@BeforeEach
	void setUp() {
		userService = new UserService(userRepository, userSessionRepository, passwordEncoder);
		when(userRepository.findById(1L)).thenReturn(Optional.of(user));
		when(user.getStatus()).thenReturn(UserStatus.ACTIVE);
		when(user.getPasswordHash()).thenReturn("old-hash");
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
}
