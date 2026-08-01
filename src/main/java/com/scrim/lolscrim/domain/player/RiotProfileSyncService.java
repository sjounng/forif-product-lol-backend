package com.scrim.lolscrim.domain.player;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.scrim.lolscrim.domain.player.RiotApiClient.LeagueEntry;
import com.scrim.lolscrim.domain.player.RiotApiClient.RiotProfileLookup;
import com.scrim.lolscrim.domain.player.RiotApiClient.RiotRankLookup;
import com.scrim.lolscrim.global.error.ApiException;
import com.scrim.lolscrim.global.error.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RiotProfileSyncService {

	private final RiotApiClient riotApiClient;
	private final RiotAccountRepository accountRepository;
	private final RiotRankSnapshotRepository rankRepository;
	private final Clock clock;

	@Transactional
	public SyncedRiotProfile sync(String gameName, String tagLine) {
		RiotProfileLookup lookup = riotApiClient.fetchProfile(gameName.trim(), tagLine.trim());
		LocalDateTime now = LocalDateTime.now(clock);
		RiotAccount account = accountRepository.findByPuuid(lookup.account().puuid())
				.orElseGet(() -> accountRepository.save(RiotAccount.create(
						lookup.account().puuid(), lookup.account().gameName(), lookup.account().tagLine(), now)));
		account.refreshProfile(
				lookup.account().gameName(),
				lookup.account().tagLine(),
				lookup.summoner().id(),
				lookup.summoner().profileIconId(),
				lookup.summoner().summonerLevel(),
				lookup.primaryLane(),
				lookup.secondaryLane(),
				now);
		RiotRankSnapshot rank = saveRank(account.getId(), lookup.soloRank(), now);
		return new SyncedRiotProfile(account, rank);
	}

	@Transactional
	public SyncedRiotProfile syncRank(Long accountId) {
		RiotAccount account = accountRepository.findById(accountId)
				.orElseThrow(() -> new ApiException(
						ErrorCode.RIOT_ACCOUNT_NOT_FOUND,
						"Riot 계정을 찾을 수 없습니다."));
		RiotRankLookup lookup = riotApiClient.fetchRank(account.getPuuid());
		LocalDateTime now = LocalDateTime.now(clock);
		account.refreshProfile(
				account.getGameName(),
				account.getTagLine(),
				lookup.summoner().id(),
				lookup.summoner().profileIconId(),
				lookup.summoner().summonerLevel(),
				account.getPrimaryLane(),
				account.getSecondaryLane(),
				now);
		RiotRankSnapshot rank = saveRank(account.getId(), lookup.soloRank(), now);
		return new SyncedRiotProfile(account, rank);
	}

	private RiotRankSnapshot saveRank(Long accountId, LeagueEntry solo, LocalDateTime now) {
		return rankRepository.save(RiotRankSnapshot.create(
				accountId,
				solo == null ? "UNRANKED" : solo.tier(),
				solo == null ? null : solo.rank(),
				solo == null ? 0 : solo.leaguePoints(),
				solo == null ? 0 : solo.wins(),
				solo == null ? 0 : solo.losses(),
				now));
	}

	public record SyncedRiotProfile(RiotAccount account, RiotRankSnapshot rank) {
	}
}
