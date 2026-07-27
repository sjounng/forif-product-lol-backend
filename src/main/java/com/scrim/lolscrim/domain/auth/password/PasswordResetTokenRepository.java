package com.scrim.lolscrim.domain.auth.password;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

	Optional<PasswordResetToken> findByTokenHash(String tokenHash);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			update PasswordResetToken token
			set token.usedAt = :usedAt
			where token.userId = :userId
			  and token.usedAt is null
			""")
	int markAllUsedByUserId(@Param("userId") Long userId, @Param("usedAt") java.time.LocalDateTime usedAt);
}
