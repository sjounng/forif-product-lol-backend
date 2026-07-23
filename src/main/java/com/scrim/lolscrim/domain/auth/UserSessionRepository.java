package com.scrim.lolscrim.domain.auth;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserSessionRepository extends JpaRepository<UserSession, Long> {

	Optional<UserSession> findByRefreshTokenHash(String refreshTokenHash);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			update UserSession session
			set session.revokedAt = :revokedAt
			where session.userId = :userId
			  and session.revokedAt is null
			""")
	int revokeAllByUserId(@Param("userId") Long userId, @Param("revokedAt") java.time.LocalDateTime revokedAt);
}
