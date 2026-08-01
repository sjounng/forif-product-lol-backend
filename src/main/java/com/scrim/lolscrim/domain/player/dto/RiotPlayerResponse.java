package com.scrim.lolscrim.domain.player.dto;

import java.util.Map;

import com.scrim.lolscrim.domain.player.PlayerRating;
import com.scrim.lolscrim.domain.player.RiotAccount;
import com.scrim.lolscrim.domain.player.RiotRankSnapshot;
import com.scrim.lolscrim.domain.session.Lane;
import com.scrim.lolscrim.domain.session.Player;

public record RiotPlayerResponse(
		Long id,
		Long memberUserId,
		String displayName,
		RiotAccountResponse riotAccount,
		int rating,
		int rd,
		int gamesPlayed,
		int wins,
		int losses,
		String primaryLane,
		String secondaryLane,
		Map<Lane, Integer> lanePool,
		boolean isActive) {

	public static RiotPlayerResponse from(
			Player player,
			RiotAccount account,
			RiotRankSnapshot rank,
			PlayerRating rating) {
		return new RiotPlayerResponse(
				player.getId(),
				player.getMemberUserId(),
				player.getDisplayName(),
				RiotAccountResponse.from(account, rank),
				rating == null ? 1500 : rating.getRating(),
				rating == null ? 350 : rating.getRd(),
				rating == null ? 0 : rating.getGamesPlayed(),
				rating == null ? 0 : rating.getWins(),
				rating == null ? 0 : rating.getLosses(),
				account.getPrimaryLane() == null ? null : account.getPrimaryLane().name(),
				account.getSecondaryLane() == null ? null : account.getSecondaryLane().name(),
				Map.of(),
				player.isActive());
	}
}
