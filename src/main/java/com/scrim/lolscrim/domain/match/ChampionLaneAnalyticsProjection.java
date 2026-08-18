package com.scrim.lolscrim.domain.match;

import com.scrim.lolscrim.domain.player.Lane;

public interface ChampionLaneAnalyticsProjection {

	Integer getChampionId();

	Lane getLane();

	Long getPicks();

	Long getWins();

	Double getKdaSum();

	Long getKdaSamples();
}
