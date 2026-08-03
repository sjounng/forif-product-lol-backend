package com.scrim.lolscrim.domain.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.scrim.lolscrim.domain.riot.LaneHistoryResult;
import com.scrim.lolscrim.domain.riot.QueueType;
import com.scrim.lolscrim.domain.riot.RankDivision;
import com.scrim.lolscrim.domain.riot.RiotAccount;
import com.scrim.lolscrim.domain.riot.RiotAccountRepository;
import com.scrim.lolscrim.domain.riot.RiotAccountService;
import com.scrim.lolscrim.domain.riot.RiotMatchService;
import com.scrim.lolscrim.domain.riot.RiotPlatform;
import com.scrim.lolscrim.domain.riot.RiotProfileFetch;
import com.scrim.lolscrim.domain.riot.RiotRankSnapshot;
import com.scrim.lolscrim.domain.riot.RiotRankSnapshotRepository;
import com.scrim.lolscrim.domain.riot.RiotSyncResult;
import com.scrim.lolscrim.domain.riot.RiotSyncStatus;
import com.scrim.lolscrim.domain.riot.Tier;
import com.scrim.lolscrim.global.error.ApiException;
import com.scrim.lolscrim.global.error.ErrorCode;

@ExtendWith(MockitoExtension.class)
class RiotProfileSyncServiceTest {

	@Mock private RiotAccountService riotAccountService;
	@Mock private RiotMatchService riotMatchService;
	@Mock private RiotAccountRepository accountRepository;
	@Mock private RiotRankSnapshotRepository rankRepository;

	private RiotProfileSyncService service;

	@BeforeEach
	void setUp() {
		service = new RiotProfileSyncService(
				riotAccountService, riotMatchService, accountRepository, rankRepository);
	}

	@Test
	void syncUsesCanonicalRiotDataAndStoresPreferredLanes() {
		RiotProfileFetch fetch = new RiotProfileFetch(
				RiotSyncStatus.OK, "puuid-1", "Player One", "KR1", 123, 456,
				QueueType.RANKED_SOLO_5x5, Tier.DIAMOND, RankDivision.II, 42, 10, 5, 2642);
		RiotAccount account = RiotAccount.create("puuid-1", RiotPlatform.KR, "Player One", "KR1");
		ReflectionTestUtils.setField(account, "id", 11L);
		RiotRankSnapshot rank = RiotRankSnapshot.create(
				11L, QueueType.RANKED_SOLO_5x5, Tier.DIAMOND, RankDivision.II, 42, 10, 5, 2642);

		when(riotAccountService.fetchProfile("Player One", "KR1")).thenReturn(fetch);
		when(riotMatchService.fetchRecentLaneHistory("puuid-1"))
				.thenReturn(LaneHistoryResult.of(RiotSyncStatus.OK, Map.of(Lane.MID, 12, Lane.TOP, 3), 15));
		when(riotAccountService.persistProfile(fetch)).thenReturn(RiotSyncResult.ranked(
				11L, QueueType.RANKED_SOLO_5x5, Tier.DIAMOND, RankDivision.II, 42, 2642));
		when(accountRepository.findById(11L)).thenReturn(Optional.of(account));
		when(rankRepository.findFirstByRiotAccountIdAndQueueTypeOrderByCapturedAtDesc(
				11L, QueueType.RANKED_SOLO_5x5)).thenReturn(Optional.of(rank));

		RiotProfileSyncService.SyncedRiotProfile synced = service.sync("Player One", "KR1");

		assertThat(synced.account()).isSameAs(account);
		assertThat(synced.rank()).isSameAs(rank);
		assertThat(account.getPrimaryLane()).isEqualTo(Lane.MID);
		assertThat(account.getSecondaryLane()).isEqualTo(Lane.TOP);
		assertThat(synced.lanePool().get(Lane.MID)).isEqualTo(5);
	}

	@Test
	void rateLimitIsExposedAsDomainError() {
		when(riotAccountService.fetchProfile("Player One", "KR1"))
				.thenReturn(RiotProfileFetch.failed(
						RiotSyncStatus.RATE_LIMITED, null, "Player One", "KR1"));

		assertThatThrownBy(() -> service.sync("Player One", "KR1"))
				.isInstanceOfSatisfying(ApiException.class, exception ->
						assertThat(exception.getCode()).isEqualTo(ErrorCode.RIOT_API_RATE_LIMITED));
	}
}
