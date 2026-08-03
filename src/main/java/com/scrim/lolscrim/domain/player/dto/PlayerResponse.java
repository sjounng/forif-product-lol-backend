package com.scrim.lolscrim.domain.player.dto;

import com.scrim.lolscrim.domain.player.Player;
import com.scrim.lolscrim.domain.player.PlayerRating;
import com.scrim.lolscrim.domain.riot.RankDivision;
import com.scrim.lolscrim.domain.riot.RiotSyncStatus;
import com.scrim.lolscrim.domain.riot.Tier;

public record PlayerResponse(
		Long id,
		String displayName,
		RiotAccountSummary riotAccount,
		RiotSyncStatus riotSyncStatus,
		int rating,
		int rd,
		int gamesPlayed,
		int wins,
		int losses,
		boolean isActive) {

	public record RiotAccountSummary(
			String gameName, String tagLine, Tier tier, RankDivision division, int leaguePoints) {
	}

	public static PlayerResponse of(
			Player player, PlayerRating rating, RiotAccountSummary riotAccount, RiotSyncStatus syncStatus) {
		return new PlayerResponse(
				player.getId(),
				player.getDisplayName(),
				riotAccount,
				syncStatus,
				rating.getRating(),
				rating.getRd(),
				rating.getGamesPlayed(),
				rating.getWins(),
				rating.getLosses(),
				player.isActive());
	}
}
