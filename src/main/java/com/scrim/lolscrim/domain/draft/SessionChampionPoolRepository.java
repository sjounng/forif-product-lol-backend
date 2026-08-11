package com.scrim.lolscrim.domain.draft;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionChampionPoolRepository
		extends JpaRepository<SessionChampionPool, SessionChampionPoolId> {

	List<SessionChampionPool> findAllBySessionId(Long sessionId);
}
