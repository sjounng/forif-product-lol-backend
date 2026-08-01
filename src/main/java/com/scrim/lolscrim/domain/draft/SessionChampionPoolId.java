package com.scrim.lolscrim.domain.draft;

import java.io.Serializable;

public record SessionChampionPoolId(Long sessionId, Integer championId, String side) implements Serializable {
}
