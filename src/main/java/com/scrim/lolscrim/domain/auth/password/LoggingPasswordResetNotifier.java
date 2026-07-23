package com.scrim.lolscrim.domain.auth.password;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@Profile("!prod")
public class LoggingPasswordResetNotifier implements PasswordResetNotifier {

	@Override
	public void send(String email, String resetToken) {
		log.info("Development password reset token for {}: {}", email, resetToken);
	}
}
