package com.scrim.lolscrim.domain.player;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RiotRankSnapshotRepository extends JpaRepository<RiotRankSnapshot, Long> {

	Optional<RiotRankSnapshot> findFirstByRiotAccountIdAndQueueTypeOrderByCapturedAtDesc(
			Long riotAccountId,
			String queueType);

	List<RiotRankSnapshot> findAllByRiotAccountIdInAndQueueTypeOrderByCapturedAtDesc(
			Collection<Long> riotAccountIds,
			String queueType);
}
