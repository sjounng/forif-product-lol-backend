package com.scrim.lolscrim.domain.riot;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.scrim.lolscrim.domain.riot.dto.RiotAccountDto;
import com.scrim.lolscrim.domain.riot.dto.RiotLeagueEntryDto;
import com.scrim.lolscrim.domain.riot.dto.RiotSummonerDto;

import lombok.RequiredArgsConstructor;

/**
 * "GameName#TAG" 문자열 하나로 account-v1(puuid 조회) -&gt; summoner-v4(프로필 메타)와
 * league-v4(랭크, puuid 기반)를 조회해서 riot_accounts/riot_rank_snapshots 에 반영한다.
 * summoner-v4 가 summonerId를 더 이상 안 줘서, league-v4도 by-summoner가 아니라 by-puuid로 부른다.
 */
@Service
@RequiredArgsConstructor
public class RiotAccountService {

	private final RiotApiClient riotApiClient;
	private final RiotAccountRepository riotAccountRepository;
	private final RiotRankSnapshotRepository riotRankSnapshotRepository;

	@Value("${app.riot.platform}")
	private RiotPlatform platform;

	@Transactional
	public RiotSyncResult syncByRiotId(String gameName, String tagLine) {
		RiotLookupResult<RiotAccountDto> accountResult = riotApiClient.lookupAccount(gameName, tagLine);
		if (!accountResult.isOk()) {
			return RiotSyncResult.failed(accountResult.status());
		}
		RiotAccountDto accountDto = accountResult.value();

		RiotAccount account = riotAccountRepository.findByPuuid(accountDto.puuid())
				.orElseGet(() -> RiotAccount.create(accountDto.puuid(), platform, gameName, tagLine));

		LocalDateTime now = LocalDateTime.now();

		RiotLookupResult<RiotSummonerDto> summonerResult = riotApiClient.lookupSummoner(accountDto.puuid());
		if (!summonerResult.isOk()) {
			account.markSyncFailed(summonerResult.status(), now);
			riotAccountRepository.save(account);
			return RiotSyncResult.failed(summonerResult.status());
		}
		RiotSummonerDto summonerDto = summonerResult.value();

		RiotLookupResult<List<RiotLeagueEntryDto>> leagueResult =
				riotApiClient.lookupLeagueEntries(accountDto.puuid());
		if (!leagueResult.isOk()) {
			account.markSyncFailed(leagueResult.status(), now);
			riotAccountRepository.save(account);
			return RiotSyncResult.failed(leagueResult.status());
		}

		account.applySummoner(summonerDto.profileIconId(), summonerDto.summonerLevel(), now);
		account = riotAccountRepository.save(account);

		RiotLeagueEntryDto entry = findEntry(leagueResult.value(), "RANKED_SOLO_5x5");
		QueueType queueType = QueueType.RANKED_SOLO_5x5;
		if (entry == null) {
			entry = findEntry(leagueResult.value(), "RANKED_FLEX_SR");
			queueType = QueueType.RANKED_FLEX_SR;
		}
		if (entry == null) {
			return RiotSyncResult.unranked(account.getId());
		}

		Tier tier = Tier.valueOf(entry.tier());
		RankDivision division = parseDivision(entry.rank());
		int ladderScore = LadderScoreCalculator.calculate(tier, division, entry.leaguePoints());

		riotRankSnapshotRepository.save(RiotRankSnapshot.create(
				account.getId(), queueType, tier, division, entry.leaguePoints(), entry.wins(), entry.losses(),
				ladderScore));

		return RiotSyncResult.ranked(account.getId(), queueType, tier, division, entry.leaguePoints(), ladderScore);
	}

	private static RiotLeagueEntryDto findEntry(List<RiotLeagueEntryDto> entries, String queueType) {
		return entries.stream()
				.filter(e -> queueType.equals(e.queueType()))
				.findFirst()
				.orElse(null);
	}

	private static RankDivision parseDivision(String rank) {
		return (rank == null || rank.isBlank()) ? null : RankDivision.valueOf(rank);
	}
}
