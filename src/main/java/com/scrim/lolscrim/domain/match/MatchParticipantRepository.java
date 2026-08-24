package com.scrim.lolscrim.domain.match;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MatchParticipantRepository extends JpaRepository<MatchParticipant, Long> {

	List<MatchParticipant> findAllByMatchId(Long matchId);

	List<MatchParticipant> findAllByMatchIdInOrderByMatchIdAscSideAscLaneAsc(Collection<Long> matchIds);

	@Query("""
			select participant.championId as championId,
			       participant.lane as lane,
			       count(participant) as picks,
			       sum(case when participant.win = true then 1L else 0L end) as wins,
			       sum(case
			               when participant.kills is not null
			                and participant.deaths is not null
			                and participant.assists is not null
			               then ((participant.kills + participant.assists) * 1.0)
			                    / (case when participant.deaths = 0 then 1 else participant.deaths end)
			               else 0.0
			           end) as kdaSum,
			       sum(case
			               when participant.kills is not null
			                and participant.deaths is not null
			                and participant.assists is not null
			               then 1L
			               else 0L
			           end) as kdaSamples
			from MatchParticipant participant
			where participant.championId is not null
			  and participant.matchId in (
			      select match.id from ScrimMatch match where match.status = :status
			  )
			group by participant.championId, participant.lane
			""")
	List<ChampionLaneAnalyticsProjection> aggregateChampionAnalyticsByMatchStatus(
			@Param("status") MatchStatus status);

	@Query("""
			select count(distinct participant.matchId)
			from MatchParticipant participant
			where participant.championId is not null
			  and participant.matchId in (
			      select match.id from ScrimMatch match where match.status = :status
			  )
			""")
	long countMatchesWithChampionByMatchStatus(@Param("status") MatchStatus status);
}
