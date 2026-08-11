package com.scrim.lolscrim.domain.group;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface GuestSessionRepository extends JpaRepository<GuestSession, Long> {

	Optional<GuestSession> findByTokenHash(String tokenHash);

	List<GuestSession> findAllByRoomIdOrderByCreatedAtDesc(Long roomId);

	@Query("""
			select guest
			from GuestSession guest
			where guest.roomId = :roomId
			  and guest.banned = false
			  and (guest.expiresAt is null or guest.expiresAt > :now)
			order by guest.createdAt desc
			""")
	List<GuestSession> findActiveByRoomId(
			@Param("roomId") Long roomId,
			@Param("now") LocalDateTime now);

	@Query("""
			select count(guest)
			from GuestSession guest
			where guest.roomId = :roomId
			  and guest.banned = false
			  and (guest.expiresAt is null or guest.expiresAt > :now)
			""")
	long countActiveByRoomId(@Param("roomId") Long roomId, @Param("now") LocalDateTime now);

}
