package com.scrim.lolscrim.domain.riot;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.scrim.lolscrim.domain.riot.dto.RiotAccountDto;
import com.scrim.lolscrim.domain.riot.dto.RiotLeagueEntryDto;
import com.scrim.lolscrim.domain.riot.dto.RiotSummonerDto;

import lombok.RequiredArgsConstructor;

/**
 * "GameName#TAG" 로 account-v1(puuid) -&gt; summoner-v4(프로필 메타) -&gt; league-v4(랭크, puuid 기반)를 조회한다.
 * summoner-v4 가 summonerId 를 더 이상 안 줘서, league-v4 도 by-summoner 가 아니라 by-puuid 로 부른다.
 *
 * <b>조회({@link #fetchProfile})와 저장({@link #persistProfile})이 분리돼 있다.</b>
 * Riot 호출은 느리고 자주 실패하므로 트랜잭션 밖에서 끝내고, 결과만 들고 들어와 저장한다.
 */
@Service
@RequiredArgsConstructor
public class RiotAccountService {

	private final RiotApiClient riotApiClient;
	private final RiotAccountRepository riotAccountRepository;
	private final RiotRankSnapshotRepository riotRankSnapshotRepository;

	@Value("${app.riot.platform}")
	private RiotPlatform platform;

	/** 순수 HTTP 조회. 트랜잭션을 열지 않는다. */
	public RiotProfileFetch fetchProfile(String gameName, String tagLine) {
		RiotLookupResult<RiotAccountDto> accountResult = riotApiClient.lookupAccount(gameName, tagLine);
		if (!accountResult.isOk()) {
			return RiotProfileFetch.failed(accountResult.status(), null, gameName, tagLine);
		}
		RiotAccountDto accountDto = accountResult.value();
		String puuid = accountDto.puuid();

		RiotLookupResult<RiotSummonerDto> summonerResult = riotApiClient.lookupSummoner(puuid);
		if (!summonerResult.isOk()) {
			return RiotProfileFetch.failed(summonerResult.status(), puuid, gameName, tagLine);
		}
		RiotSummonerDto summonerDto = summonerResult.value();

		RiotLookupResult<List<RiotLeagueEntryDto>> leagueResult = riotApiClient.lookupLeagueEntries(puuid);
		if (!leagueResult.isOk()) {
			return RiotProfileFetch.failed(leagueResult.status(), puuid, gameName, tagLine);
		}

		// 솔로/듀오 우선, 없으면 자유 랭크로 폴백 (DESIGN §4.1)
		RiotLeagueEntryDto entry = findEntry(leagueResult.value(), "RANKED_SOLO_5x5");
		QueueType queueType = QueueType.RANKED_SOLO_5x5;
		if (entry == null) {
			entry = findEntry(leagueResult.value(), "RANKED_FLEX_SR");
			queueType = QueueType.RANKED_FLEX_SR;
		}
		if (entry == null) {
			return new RiotProfileFetch(RiotSyncStatus.OK, puuid, gameName, tagLine,
					summonerDto.profileIconId(), summonerDto.summonerLevel(),
					null, Tier.UNRANKED, null, 0, 0, 0, 0);
		}

		Tier tier = Tier.valueOf(entry.tier());
		RankDivision division = parseDivision(entry.rank());
		int ladderScore = LadderScoreCalculator.calculate(tier, division, entry.leaguePoints());

		return new RiotProfileFetch(RiotSyncStatus.OK, puuid, gameName, tagLine,
				summonerDto.profileIconId(), summonerDto.summonerLevel(),
				queueType, tier, division, entry.leaguePoints(), entry.wins(), entry.losses(), ladderScore);
	}

	/** 조회 결과를 riot_accounts/riot_rank_snapshots 에 반영하고 riot_account_id 를 돌려준다. */
	@Transactional
	public RiotSyncResult persistProfile(RiotProfileFetch fetch) {
		if (fetch.puuid() == null) {
			// account-v1 단계에서 실패해 puuid 조차 못 받은 경우 — 남길 것이 없다
			return RiotSyncResult.failed(fetch.status());
		}
		LocalDateTime now = LocalDateTime.now();
		RiotAccount account = riotAccountRepository.findByPuuid(fetch.puuid())
				.orElseGet(() -> RiotAccount.create(fetch.puuid(), platform, fetch.gameName(), fetch.tagLine()));

		if (!fetch.isOk()) {
			account.markSyncFailed(fetch.status(), now);
			riotAccountRepository.save(account);
			return RiotSyncResult.failed(fetch.status());
		}

		account.applySummoner(fetch.profileIconId(), fetch.summonerLevel(), now);
		account = riotAccountRepository.save(account);

		if (!fetch.isRanked()) {
			return RiotSyncResult.unranked(account.getId());
		}

		riotRankSnapshotRepository.save(RiotRankSnapshot.create(
				account.getId(), fetch.queueType(), fetch.tier(), fetch.division(),
				fetch.leaguePoints(), fetch.wins(), fetch.losses(), fetch.ladderScore()));

		return RiotSyncResult.ranked(account.getId(), fetch.queueType(), fetch.tier(), fetch.division(),
				fetch.leaguePoints(), fetch.ladderScore());
	}

	/**
	 * 이미 등록된 적 있는 계정인지 Riot 호출 없이 먼저 확인한다.
	 * riot_accounts 는 puuid UNIQUE 라 전역 캐시 역할을 하므로, 중복 등록 시도에서 API 호출을 아낄 수 있다.
	 */
	@Transactional(readOnly = true)
	public Optional<RiotAccount> findCachedAccount(String gameName, String tagLine) {
		return riotAccountRepository.findByPlatformAndGameNameAndTagLine(platform, gameName, tagLine);
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
