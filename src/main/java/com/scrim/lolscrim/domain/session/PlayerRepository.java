package com.scrim.lolscrim.domain.session;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayerRepository extends JpaRepository<Player, Long> {

	Optional<Player> findByRoomIdAndMemberUserId(Long roomId, Long memberUserId);

	Optional<Player> findByRoomIdAndGuestSessionId(Long roomId, Long guestSessionId);

	Optional<Player> findByRoomIdAndRiotAccountId(Long roomId, Long riotAccountId);

	List<Player> findAllByRoomIdAndRiotAccountIdIsNotNullAndActiveTrueOrderByCreatedAtAsc(Long roomId);

	List<Player> findAllByRoomIdAndMemberUserIdAndActiveTrue(Long roomId, Long memberUserId);

	long countByRoomIdAndRiotAccountIdIsNotNullAndMemberUserIdIsNullAndGuestSessionIdIsNullAndActiveTrue(Long roomId);
}
