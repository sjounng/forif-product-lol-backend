package com.scrim.lolscrim.domain.player.dto;

import java.util.Map;

import com.scrim.lolscrim.domain.player.Lane;
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
		/** 라인별 숙련도 0~5 (DESIGN §5.1 lanePool). 0 = 배정 금지 */
		Map<Lane, Integer> lanePool,
		/** 최근 솔랭에서 실제로 그 라인을 간 판수 — lanePool 추천의 근거로 화면에 같이 보여준다 */
		Map<Lane, Integer> recentLaneGames,
		boolean isActive) {

	public record RiotAccountSummary(
			String gameName, String tagLine, Tier tier, RankDivision division, int leaguePoints) {
	}

	public static PlayerResponse of(
			Player player,
			PlayerRating rating,
			RiotAccountSummary riotAccount,
			RiotSyncStatus syncStatus,
			Map<Lane, Integer> lanePool,
			Map<Lane, Integer> recentLaneGames) {
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
				lanePool,
				recentLaneGames,
				player.isActive());
	}
}
