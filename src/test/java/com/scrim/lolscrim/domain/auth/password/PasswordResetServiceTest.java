package com.scrim.lolscrim.domain.auth.password;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.scrim.lolscrim.domain.auth.UserSessionRepository;
import com.scrim.lolscrim.domain.auth.password.dto.PasswordResetConfirmRequest;
import com.scrim.lolscrim.domain.auth.password.dto.PasswordResetRequest;
import com.scrim.lolscrim.domain.user.User;
import com.scrim.lolscrim.domain.user.UserRepository;
import com.scrim.lolscrim.domain.user.UserStatus;
import com.scrim.lolscrim.global.error.ApiException;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private UserSessionRepository userSessionRepository;

	@Mock
	private PasswordResetTokenRepository passwordResetTokenRepository;

	@Mock
	private PasswordResetNotifier passwordResetNotifier;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Mock
	private User user;

	@Mock
	private PasswordResetToken resetToken;

	private PasswordResetService passwordResetService;

	@BeforeEach
	void setUp() {
		passwordResetService = new PasswordResetService(
				userRepository,
				userSessionRepository,
				passwordResetTokenRepository,
				passwordResetNotifier,
				passwordEncoder);
		ReflectionTestUtils.setField(passwordResetService, "tokenTtlMinutes", 15L);
	}

	@Test
	void issuesResetTokenForMatchingActiveUser() {
		PasswordResetRequest request = new PasswordResetRequest("user@example.com");
		when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
		when(user.getStatus()).thenReturn(UserStatus.ACTIVE);
		when(user.getId()).thenReturn(1L);

		passwordResetService.requestReset(request);

		ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
		verify(passwordResetTokenRepository).markAllUsedByUserId(eq(1L), any(LocalDateTime.class));
		verify(passwordResetTokenRepository).save(tokenCaptor.capture());
		verify(passwordResetNotifier).send(eq("user@example.com"), anyString());
		org.assertj.core.api.Assertions.assertThat(tokenCaptor.getValue().getUserId()).isEqualTo(1L);
		org.assertj.core.api.Assertions.assertThat(tokenCaptor.getValue().getTokenHash()).hasSize(64);
	}

	@Test
	void doesNotRevealOrIssueTokenWhenAccountDoesNotExist() {
		PasswordResetRequest request = new PasswordResetRequest("missing@example.com");
		when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

		passwordResetService.requestReset(request);

		verify(passwordResetTokenRepository, never()).save(any());
		verify(passwordResetNotifier, never()).send(anyString(), anyString());
	}

	@Test
	void confirmsResetAndRevokesTokensAndSessions() {
		PasswordResetConfirmRequest request = new PasswordResetConfirmRequest("raw-token", "new-password");
		when(passwordResetTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(resetToken));
		when(resetToken.isUsable(any(LocalDateTime.class))).thenReturn(true);
		when(resetToken.getUserId()).thenReturn(1L);
		when(userRepository.findById(1L)).thenReturn(Optional.of(user));
		when(user.getStatus()).thenReturn(UserStatus.ACTIVE);
		when(user.getId()).thenReturn(1L);
		when(user.getPasswordHash()).thenReturn("old-hash");
		when(passwordEncoder.matches("new-password", "old-hash")).thenReturn(false);
		when(passwordEncoder.encode("new-password")).thenReturn("new-hash");

		passwordResetService.confirmReset(request);

		verify(user).changePassword("new-hash");
		verify(passwordResetTokenRepository).markAllUsedByUserId(eq(1L), any(LocalDateTime.class));
		verify(userSessionRepository).revokeAllByUserId(eq(1L), any(LocalDateTime.class));
	}

	@Test
	void rejectsInvalidExpiredOrAlreadyUsedToken() {
		PasswordResetConfirmRequest request = new PasswordResetConfirmRequest("invalid-token", "new-password");
		when(passwordResetTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(resetToken));
		when(resetToken.isUsable(any(LocalDateTime.class))).thenReturn(false);

		assertThatThrownBy(() -> passwordResetService.confirmReset(request))
				.isInstanceOf(ApiException.class)
				.hasMessage("유효하지 않거나 만료된 재설정 토큰입니다.");

		verify(userRepository, never()).findById(any());
		verify(userSessionRepository, never()).revokeAllByUserId(any(), any());
	}

	@Test
	void rejectsResetToCurrentPassword() {
		PasswordResetConfirmRequest request = new PasswordResetConfirmRequest("raw-token", "same-password");
		when(passwordResetTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(resetToken));
		when(resetToken.isUsable(any(LocalDateTime.class))).thenReturn(true);
		when(resetToken.getUserId()).thenReturn(1L);
		when(userRepository.findById(1L)).thenReturn(Optional.of(user));
		when(user.getStatus()).thenReturn(UserStatus.ACTIVE);
		when(user.getPasswordHash()).thenReturn("old-hash");
		when(passwordEncoder.matches("same-password", "old-hash")).thenReturn(true);

		assertThatThrownBy(() -> passwordResetService.confirmReset(request))
				.isInstanceOf(ApiException.class)
				.hasMessage("새 비밀번호는 현재 비밀번호와 달라야 합니다.");

		verify(user, never()).changePassword(anyString());
		verify(userSessionRepository, never()).revokeAllByUserId(any(), any());
	}
}
