package com.scrim.lolscrim.domain.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.scrim.lolscrim.domain.player.RiotApiClient.LeagueEntry;
import com.scrim.lolscrim.domain.player.RiotApiClient.RiotRankLookup;
import com.scrim.lolscrim.domain.player.RiotApiClient.SummonerLookup;
import com.scrim.lolscrim.domain.player.RiotProfileSyncService.SyncedRiotProfile;

@ExtendWith(MockitoExtension.class)
class RiotProfileSyncServiceTest {

	@Mock
	private RiotApiClient riotApiClient;
	@Mock
	private RiotAccountRepository accountRepository;
	@Mock
	private RiotRankSnapshotRepository rankRepository;

	private RiotProfileSyncService service;

	@BeforeEach
	void setUp() {
		Clock clock = Clock.fixed(
				Instant.parse("2026-07-31T15:00:00Z"),
				ZoneId.of("Asia/Seoul"));
		service = new RiotProfileSyncService(riotApiClient, accountRepository, rankRepository, clock);
	}

	@Test
	void refreshesRankForExistingAccountWithoutFetchingRiotIdAgain() {
		LocalDateTime createdAt = LocalDateTime.of(2026, 7, 1, 0, 0);
		RiotAccount account = RiotAccount.create("puuid-1", "Player One", "KR1", createdAt);
		ReflectionTestUtils.setField(account, "id", 11L);
		when(accountRepository.findById(11L)).thenReturn(Optional.of(account));
		when(riotApiClient.fetchRank("puuid-1")).thenReturn(new RiotRankLookup(
				new SummonerLookup("summoner-1", 123, 456),
				new LeagueEntry("RANKED_SOLO_5x5", "DIAMOND", "II", 42, 10, 5)));
		when(rankRepository.save(any(RiotRankSnapshot.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		SyncedRiotProfile synced = service.syncRank(11L);

		assertThat(synced.account().getSummonerId()).isEqualTo("summoner-1");
		assertThat(synced.account().getSummonerLevel()).isEqualTo(456);
		assertThat(synced.rank().getTier()).isEqualTo("DIAMOND");
		assertThat(synced.rank().getDivision()).isEqualTo("II");
		assertThat(synced.rank().getLeaguePoints()).isEqualTo(42);
		assertThat(synced.rank().getLadderScore()).isEqualTo(2642);
	}
}
