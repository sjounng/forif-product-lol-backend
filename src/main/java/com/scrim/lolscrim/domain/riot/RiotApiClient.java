package com.scrim.lolscrim.domain.riot;

import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.scrim.lolscrim.domain.riot.dto.RiotAccountDto;
import com.scrim.lolscrim.domain.riot.dto.RiotLeagueEntryDto;
import com.scrim.lolscrim.domain.riot.dto.RiotSummonerDto;

/**
 * Riot API 조회: account-v1(지역 라우팅) -&gt; summoner-v4, league-v4(둘 다 puuid 기반, 플랫폼 라우팅).
 * summoner-v4 는 더 이상 summonerId/accountId 를 주지 않아서(puuid만), league-v4 도
 * by-summoner 가 아니라 by-puuid 로 조회한다.
 * 개발 키 기준 20req/s, 24시간 만료 — 호출 실패는 예외가 아니라 {@link RiotLookupResult} 로 분류해서 돌려준다.
 */
@Component
public class RiotApiClient {

	private static final Logger log = LoggerFactory.getLogger(RiotApiClient.class);
	private static final String TOKEN_HEADER = "X-Riot-Token";

	/**
	 * 타임아웃이 없으면 상대가 응답을 안 줄 때 호출이 무기한 매달린다.
	 * 등록 한 건이 여러 콜을 연쇄로 하므로 상한을 반드시 정해둔다.
	 */
	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
	private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

	private final RestClient accountClient;
	private final RestClient platformClient;
	private final String apiKey;

	public RiotApiClient(
			@Value("${app.riot.api-key}") String apiKey,
			@Value("${app.riot.account-region}") String accountRegion,
			@Value("${app.riot.platform}") String platform) {
		this.apiKey = apiKey;
		ClientHttpRequestFactory requestFactory = timeoutRequestFactory();
		this.accountClient = RestClient.builder()
				.requestFactory(requestFactory)
				.baseUrl("https://" + accountRegion + ".api.riotgames.com")
				.build();
		this.platformClient = RestClient.builder()
				.requestFactory(requestFactory)
				.baseUrl("https://" + platform.toLowerCase() + ".api.riotgames.com")
				.build();
	}

	public RiotLookupResult<RiotAccountDto> lookupAccount(String gameName, String tagLine) {
		return execute(() -> accountClient.get()
				.uri("/riot/account/v1/accounts/by-riot-id/{gameName}/{tagLine}", gameName, tagLine)
				.header(TOKEN_HEADER, apiKey)
				.retrieve()
				.body(RiotAccountDto.class));
	}

	public RiotLookupResult<RiotSummonerDto> lookupSummoner(String puuid) {
		return execute(() -> platformClient.get()
				.uri("/lol/summoner/v4/summoners/by-puuid/{puuid}", puuid)
				.header(TOKEN_HEADER, apiKey)
				.retrieve()
				.body(RiotSummonerDto.class));
	}

	public RiotLookupResult<List<RiotLeagueEntryDto>> lookupLeagueEntries(String puuid) {
		return execute(() -> platformClient.get()
				.uri("/lol/league/v4/entries/by-puuid/{puuid}", puuid)
				.header(TOKEN_HEADER, apiKey)
				.retrieve()
				.body(new ParameterizedTypeReference<List<RiotLeagueEntryDto>>() {
				}));
	}

	private static ClientHttpRequestFactory timeoutRequestFactory() {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(CONNECT_TIMEOUT);
		factory.setReadTimeout(READ_TIMEOUT);
		return factory;
	}

	private <T> RiotLookupResult<T> execute(Supplier<T> call) {
		try {
			return RiotLookupResult.ok(call.get());
		} catch (HttpClientErrorException.NotFound e) {
			return RiotLookupResult.notFound();
		} catch (HttpClientErrorException.TooManyRequests e) {
			log.warn("Riot API rate limited: {}", e.getMessage());
			return RiotLookupResult.rateLimited();
		} catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden e) {
			// 키 문제는 일시 장애가 아니라 설정 문제다. 이걸 놓치면 모든 플레이어가 조용히
			// 기본 시드(1500)로 등록되면서 아무도 눈치채지 못한다. 개발 키는 24시간 만료다.
			log.error("Riot API 키가 유효하지 않습니다 (만료/오타 확인 필요). RIOT_API_KEY 를 갱신하세요. {}",
					e.getMessage());
			return RiotLookupResult.error();
		} catch (RestClientException e) {
			log.error("Riot API call failed", e);
			return RiotLookupResult.error();
		}
	}
}
