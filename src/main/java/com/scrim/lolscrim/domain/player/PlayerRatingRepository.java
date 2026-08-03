package com.scrim.lolscrim.domain.player;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayerRatingRepository extends JpaRepository<PlayerRating, Long> {

	List<PlayerRating> findAllByPlayerIdIn(Collection<Long> playerIds);
}
