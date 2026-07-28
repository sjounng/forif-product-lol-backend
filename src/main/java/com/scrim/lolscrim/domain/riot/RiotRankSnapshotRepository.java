package com.scrim.lolscrim.domain.riot;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RiotRankSnapshotRepository extends JpaRepository<RiotRankSnapshot, Long> {

	Optional<RiotRankSnapshot> findFirstByRiotAccountIdAndQueueTypeOrderByCapturedAtDesc(
			Long riotAccountId, QueueType queueType);
}
