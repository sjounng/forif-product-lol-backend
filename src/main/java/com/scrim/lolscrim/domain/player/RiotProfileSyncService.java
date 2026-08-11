package com.scrim.lolscrim.domain.player;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.scrim.lolscrim.domain.riot.LaneHistoryResult;
import com.scrim.lolscrim.domain.riot.QueueType;
import com.scrim.lolscrim.domain.riot.RiotAccount;
import com.scrim.lolscrim.domain.riot.RiotAccountRepository;
import com.scrim.lolscrim.domain.riot.RiotAccountService;
import com.scrim.lolscrim.domain.riot.RiotMatchService;
import com.scrim.lolscrim.domain.riot.RiotProfileFetch;
import com.scrim.lolscrim.domain.riot.RiotRankSnapshot;
import com.scrim.lolscrim.domain.riot.RiotRankSnapshotRepository;
import com.scrim.lolscrim.domain.riot.RiotSyncResult;
import com.scrim.lolscrim.domain.riot.RiotSyncStatus;
import com.scrim.lolscrim.global.error.ApiException;
import com.scrim.lolscrim.global.error.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RiotProfileSyncService {

	private final RiotAccountService riotAccountService;
	private final RiotMatchService riotMatchService;
	private final RiotAccountRepository accountRepository;
	private final RiotRankSnapshotRepository rankRepository;

	@Transactional
	public SyncedRiotProfile sync(String gameName, String tagLine) {
		RiotProfileFetch fetch = riotAccountService.fetchProfile(gameName.trim(), tagLine.trim());
		if (!fetch.isOk()) {
			throw syncFailure(fetch.status());
		}

		LaneHistoryResult laneHistory = fetch.puuid() == null
				? LaneHistoryResult.empty(RiotSyncStatus.NOT_FOUND)
				: riotMatchService.fetchRecentLaneHistory(fetch.puuid());
		RiotSyncResult syncResult = riotAccountService.persistProfile(fetch);
		if (syncResult.riotAccountId() == null) {
			throw syncFailure(syncResult.status());
		}

		RiotAccount account = accountRepository.findById(syncResult.riotAccountId())
				.orElseThrow(() -> new ApiException(
						ErrorCode.RIOT_ACCOUNT_NOT_FOUND,
						"Riot 계정을 찾을 수 없습니다."));
		List<Lane> preferredLanes = preferredLanes(laneHistory.laneGames());
		Map<Lane, Integer> lanePool = LaneProficiencyCalculator.recommend(laneHistory.laneGames());
		account.applyLanePreferences(
				preferredLanes.isEmpty() ? null : preferredLanes.get(0),
				preferredLanes.size() < 2 ? null : preferredLanes.get(1));

		RiotRankSnapshot rank = rankRepository
				.findFirstByRiotAccountIdAndQueueTypeOrderByCapturedAtDesc(
						account.getId(), QueueType.RANKED_SOLO_5x5)
				.orElse(null);
		return new SyncedRiotProfile(account, rank, lanePool);
	}

	@Transactional
	public SyncedRiotProfile syncRank(Long accountId) {
		RiotAccount account = accountRepository.findById(accountId)
				.orElseThrow(() -> new ApiException(
						ErrorCode.RIOT_ACCOUNT_NOT_FOUND,
						"Riot 계정을 찾을 수 없습니다."));
		return sync(account.getGameName(), account.getTagLine());
	}

	private static List<Lane> preferredLanes(Map<Lane, Integer> laneGames) {
		return laneGames.entrySet().stream()
				.filter(entry -> entry.getValue() > 0)
				.sorted(Map.Entry.<Lane, Integer>comparingByValue(Comparator.reverseOrder())
						.thenComparing(entry -> entry.getKey().ordinal()))
				.map(Map.Entry::getKey)
				.limit(2)
				.toList();
	}

	private static ApiException syncFailure(RiotSyncStatus status) {
		return switch (status) {
			case NOT_FOUND -> new ApiException(ErrorCode.RIOT_ACCOUNT_NOT_FOUND, "Riot 계정을 찾을 수 없습니다.");
			case RATE_LIMITED -> new ApiException(ErrorCode.RIOT_API_RATE_LIMITED, "Riot API 요청 한도를 초과했습니다.");
			case ERROR -> new ApiException(ErrorCode.RIOT_API_UNAVAILABLE, "Riot API를 사용할 수 없습니다.");
			case OK -> new ApiException(ErrorCode.RIOT_API_UNAVAILABLE, "Riot 계정 동기화에 실패했습니다.");
		};
	}

	public record SyncedRiotProfile(
			RiotAccount account,
			RiotRankSnapshot rank,
			Map<Lane, Integer> lanePool) {

		public SyncedRiotProfile {
			lanePool = Map.copyOf(lanePool);
		}
	}
}
