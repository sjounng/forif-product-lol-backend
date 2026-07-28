package com.scrim.lolscrim.domain.player;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayerRepository extends JpaRepository<Player, Long> {

	List<Player> findByRoomIdAndActiveTrueOrderByDisplayNameAsc(Long roomId);

	Optional<Player> findByIdAndRoomId(Long id, Long roomId);

	int countByRoomIdAndActiveTrue(Long roomId);

	boolean existsByRoomIdAndRiotAccountId(Long roomId, Long riotAccountId);
}
