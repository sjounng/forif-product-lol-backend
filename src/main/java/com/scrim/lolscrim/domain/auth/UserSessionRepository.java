package com.scrim.lolscrim.domain.auth;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSessionRepository extends JpaRepository<UserSession, Long> {

	Optional<UserSession> findByRefreshTokenHash(String refreshTokenHash);
}
