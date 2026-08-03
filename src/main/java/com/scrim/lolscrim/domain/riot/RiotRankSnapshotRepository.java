package com.scrim.lolscrim.domain.riot;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RiotRankSnapshotRepository extends JpaRepository<RiotRankSnapshot, Long> {

	Optional<RiotRankSnapshot> findFirstByRiotAccountIdAndQueueTypeOrderByCapturedAtDesc(
			Long riotAccountId, QueueType queueType);

	/** 명단 일괄 조회용 — 계정별 최신 1건 고르기는 호출 측에서 한다. */
	List<RiotRankSnapshot> findByRiotAccountIdIn(List<Long> riotAccountIds);
}
