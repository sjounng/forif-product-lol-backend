package com.scrim.lolscrim.domain.match;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.scrim.lolscrim.domain.session.TeamSide;

import jakarta.persistence.LockModeType;

public interface ScrimMatchRepository extends JpaRepository<ScrimMatch, Long> {

	List<ScrimMatch> findAllBySessionIdOrderByGameNoAsc(Long sessionId);

	Optional<ScrimMatch> findFirstBySessionIdOrderByGameNoDesc(Long sessionId);

	boolean existsBySessionIdAndStatusIn(Long sessionId, Collection<MatchStatus> statuses);

	long countBySessionIdAndStatusAndWinnerSide(Long sessionId, MatchStatus status, TeamSide winnerSide);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select match from ScrimMatch match where match.id = :id")
	Optional<ScrimMatch> findByIdForUpdate(@Param("id") Long id);

	/**
	 * 세션 락을 잡기 위한 sessionId 만 스칼라로 읽는다.
	 *
	 * <p>엔티티로 먼저 읽으면 안 된다 — 그러면 매치가 영속성 컨텍스트에 올라가고,
	 * 이후 {@link #findByIdForUpdate}(잠금 조회)는 <b>이미 관리 중인 인스턴스를 그대로 반환</b>한다
	 * (락만 얻고 DB 값으로 상태를 덮지 않는 것이 JPA 규약). 그 결과 다른 트랜잭션이 커밋한
	 * status/rating_applied 를 못 보고 stale 한 값으로 가드를 통과해 점수가 두 번 반영될 수 있다.
	 */
	@Query("select match.sessionId from ScrimMatch match where match.id = :id")
	Optional<Long> findSessionIdById(@Param("id") Long id);
}
