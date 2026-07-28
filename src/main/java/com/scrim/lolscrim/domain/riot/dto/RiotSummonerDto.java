package com.scrim.lolscrim.domain.riot.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * summoner-v4 GET /lol/summoner/v4/summoners/by-puuid/{puuid} 응답.
 * Riot 이 summonerId/accountId 를 이 엔드포인트에서 제거해서(현재 puuid만 옴) id 필드는 없다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RiotSummonerDto(String puuid, Integer profileIconId, Integer summonerLevel) {
}
