package com.scrim.lolscrim.domain.room;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoomRepository extends JpaRepository<Room, Long> {

	List<Room> findByOwnerUserIdOrderByCreatedAtDesc(Long ownerUserId);

	Optional<Room> findByIdAndOwnerUserId(Long id, Long ownerUserId);

	boolean existsByPublicCode(String publicCode);

	Optional<Room> findByPublicCodeAndStatus(String publicCode, RoomStatus status);

	List<Room> findAllByIdInOrderByCreatedAtDesc(Collection<Long> ids);

	@Query(value = "SELECT COUNT(*) FROM scrim_sessions WHERE room_id = :roomId", nativeQuery = true)
	long countSessions(@Param("roomId") Long roomId);

	@Query(value = "SELECT COUNT(*) FROM matches WHERE room_id = :roomId", nativeQuery = true)
	long countMatches(@Param("roomId") Long roomId);
}
