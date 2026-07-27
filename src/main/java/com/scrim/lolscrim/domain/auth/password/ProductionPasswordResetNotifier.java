package com.scrim.lolscrim.domain.auth.password;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.scrim.lolscrim.global.error.ApiException;

@Component
@Profile("prod")
public class ProductionPasswordResetNotifier implements PasswordResetNotifier {

	@Override
	public void send(String email, String resetToken) {
		throw new ApiException(
				HttpStatus.SERVICE_UNAVAILABLE,
				"비밀번호 재설정 이메일 발송 기능이 아직 설정되지 않았습니다.");
	}
}
