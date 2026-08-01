package com.scrim.lolscrim.domain.user;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

	boolean existsByEmail(String email);

	Optional<User> findByEmail(String email);

	Optional<User> findByRiotAccountId(Long riotAccountId);

	List<User> findTop10ByStatusAndDisplayNameContainingIgnoreCaseOrderByDisplayNameAsc(
			UserStatus status,
			String displayName);
}
