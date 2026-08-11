package com.scrim.lolscrim.domain.session;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionTeamMemberRepository extends JpaRepository<SessionTeamMember, Long> {

	List<SessionTeamMember> findAllBySessionIdOrderBySideAscLaneAsc(Long sessionId);
}
