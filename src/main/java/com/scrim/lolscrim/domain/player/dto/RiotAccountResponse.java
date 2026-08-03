package com.scrim.lolscrim.domain.player.dto;

import com.scrim.lolscrim.domain.riot.RiotAccount;
import com.scrim.lolscrim.domain.riot.RiotRankSnapshot;

import java.time.LocalDateTime;

public record RiotAccountResponse(
		String gameName,
		String tagLine,
		String tier,
		String division,
		int leaguePoints,
		int wins,
		int losses,
		int ladderScore,
		LocalDateTime syncedAt) {

	public static RiotAccountResponse from(RiotAccount account, RiotRankSnapshot rank) {
		return new RiotAccountResponse(
				account.getGameName(),
				account.getTagLine(),
				rank == null ? "UNRANKED" : rank.getTier().name(),
				rank == null || rank.getRankDivision() == null ? null : rank.getRankDivision().name(),
				rank == null ? 0 : rank.getLeaguePoints(),
				rank == null ? 0 : rank.getWins(),
				rank == null ? 0 : rank.getLosses(),
				rank == null ? 0 : rank.getLadderScore(),
				account.getLastSyncedAt());
	}
}
