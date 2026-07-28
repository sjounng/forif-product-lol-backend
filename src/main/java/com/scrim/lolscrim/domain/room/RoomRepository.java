package com.scrim.lolscrim.domain.room;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomRepository extends JpaRepository<Room, Long> {

	List<Room> findByOwnerUserIdOrderByCreatedAtDesc(Long ownerUserId);

	Optional<Room> findByIdAndOwnerUserId(Long id, Long ownerUserId);

	boolean existsByPublicCode(String publicCode);
}
