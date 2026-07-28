package com.scrim.lolscrim.domain.riot.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * league-v4 GET /lol/league/v4/entries/by-summoner/{summonerId} 응답의 원소 1개.
 * tier/rank 는 "GOLD"/"II" 같은 raw 문자열로 온다 — 마스터 이상은 rank 가 없거나 의미 없음(항상 "I").
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RiotLeagueEntryDto(
		String queueType,
		String tier,
		String rank,
		int leaguePoints,
		int wins,
		int losses) {
}
