package com.scrim.lolscrim.domain.auth.password;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.scrim.lolscrim.global.error.ApiException;

class ProductionPasswordResetNotifierTest {

	private final ProductionPasswordResetNotifier notifier = new ProductionPasswordResetNotifier();

	@Test
	void reportsUnavailableUntilProductionEmailDeliveryIsConfigured() {
		assertThatThrownBy(() -> notifier.send("user@example.com", "token"))
				.isInstanceOf(ApiException.class)
				.hasMessage("비밀번호 재설정 이메일 발송 기능이 아직 설정되지 않았습니다.");
	}
}
