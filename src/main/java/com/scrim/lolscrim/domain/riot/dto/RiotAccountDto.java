package com.scrim.lolscrim.domain.riot.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** account-v1 GET /riot/account/v1/accounts/by-riot-id/{gameName}/{tagLine} 응답. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RiotAccountDto(String puuid, String gameName, String tagLine) {
}
