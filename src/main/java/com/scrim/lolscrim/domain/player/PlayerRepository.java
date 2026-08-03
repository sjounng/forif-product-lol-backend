package com.scrim.lolscrim.domain.player;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayerRepository extends JpaRepository<Player, Long> {

	List<Player> findByRoomIdAndActiveTrueOrderByDisplayNameAsc(Long roomId);

	Optional<Player> findByIdAndRoomId(Long id, Long roomId);

	int countByRoomIdAndActiveTrue(Long roomId);

	boolean existsByRoomIdAndRiotAccountId(Long roomId, Long riotAccountId);

	Optional<Player> findByRoomIdAndMemberUserId(Long roomId, Long memberUserId);

	Optional<Player> findByRoomIdAndGuestSessionId(Long roomId, Long guestSessionId);

	Optional<Player> findByRoomIdAndRiotAccountId(Long roomId, Long riotAccountId);

	List<Player> findAllByRoomIdAndRiotAccountIdIsNotNullAndActiveTrueOrderByCreatedAtAsc(Long roomId);

	List<Player> findAllByRoomIdAndMemberUserIdAndActiveTrue(Long roomId, Long memberUserId);

	long countByRoomIdAndRiotAccountIdIsNotNullAndMemberUserIdIsNullAndGuestSessionIdIsNullAndActiveTrue(Long roomId);
}
