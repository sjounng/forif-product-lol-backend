package com.scrim.lolscrim.domain.group;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoomMembershipRepository extends JpaRepository<RoomMembership, Long> {

	Optional<RoomMembership> findByRoomIdAndUserId(Long roomId, Long userId);

	Optional<RoomMembership> findByRoomIdAndUserIdAndActiveTrue(Long roomId, Long userId);

	List<RoomMembership> findAllByRoomIdAndActiveTrueOrderByJoinedAtAsc(Long roomId);

	List<RoomMembership> findAllByUserIdAndActiveTrue(Long userId);

	long countByRoomIdAndActiveTrue(Long roomId);

	@Query("""
			select membership.roomId
			from RoomMembership membership
			where membership.userId = :userId and membership.active = true
			""")
	List<Long> findActiveRoomIdsByUserId(@Param("userId") Long userId);
}
