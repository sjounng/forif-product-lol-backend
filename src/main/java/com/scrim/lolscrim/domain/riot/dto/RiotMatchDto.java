package com.scrim.lolscrim.domain.riot.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * match-v5 GET /lol/match/v5/matches/{matchId} 응답에서 우리가 쓰는 부분만.
 * 라인 판정은 participant.teamPosition 을 쓴다 (레거시 lane/role 은 부정확한 경우가 있다).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RiotMatchDto(Info info) {

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Info(long gameStartTimestamp, int queueId, List<Participant> participants) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Participant(String puuid, String teamPosition) {
	}
}
