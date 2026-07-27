package com.scrim.lolscrim.domain.auth.password;

public interface PasswordResetNotifier {

	void send(String email, String resetToken);
}
