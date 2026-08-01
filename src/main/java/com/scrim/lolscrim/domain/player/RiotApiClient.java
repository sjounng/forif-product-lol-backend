package com.scrim.lolscrim.domain.player;

import static com.scrim.lolscrim.global.error.ErrorCode.RIOT_ACCOUNT_NOT_FOUND;
import static com.scrim.lolscrim.global.error.ErrorCode.RIOT_API_AUTH_FAILED;
import static com.scrim.lolscrim.global.error.ErrorCode.RIOT_API_KEY_MISSING;
import static com.scrim.lolscrim.global.error.ErrorCode.RIOT_API_RATE_LIMITED;
import static com.scrim.lolscrim.global.error.ErrorCode.RIOT_API_UNAVAILABLE;

import org.springframework.http.HttpHeaders;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.scrim.lolscrim.domain.session.Lane;

import com.scrim.lolscrim.global.error.ApiException;

public class RiotApiClient {

	private static final String RIOT_TOKEN_HEADER = "X-Riot-Token";

	private final RestClient regionalClient;
	private final RestClient platformClient;
	private final String apiKey;

	RiotApiClient(RestClient restClient, String apiKey) {
		this(restClient, restClient, apiKey);
	}

	RiotApiClient(RestClient regionalClient, RestClient platformClient, String apiKey) {
		this.regionalClient = regionalClient;
		this.platformClient = platformClient;
		this.apiKey = apiKey == null ? null : apiKey.trim();
	}

	public RiotAccountLookup fetchAccount(String gameName, String tagLine) {
		if (apiKey == null || apiKey.isBlank()) {
			throw new ApiException(
					RIOT_API_KEY_MISSING,
					"Riot API 키가 설정되지 않았습니다. 백엔드 환경 변수 RIOT_API_KEY를 설정해 주세요.");
		}
		try {
			RiotAccountLookup account = regionalClient.get()
					.uri("/riot/account/v1/accounts/by-riot-id/{gameName}/{tagLine}", gameName, tagLine)
					.header(RIOT_TOKEN_HEADER, apiKey)
					.header(HttpHeaders.ACCEPT, "application/json")
					.retrieve()
					.body(RiotAccountLookup.class);
			if (account == null || account.puuid() == null || account.puuid().isBlank()) {
				throw new ApiException(RIOT_API_UNAVAILABLE, "Riot 계정 응답이 올바르지 않습니다.");
			}
			return account;
		} catch (RestClientResponseException exception) {
			throw switch (exception.getStatusCode().value()) {
				case 404 -> new ApiException(RIOT_ACCOUNT_NOT_FOUND, "해당 Riot ID를 찾을 수 없습니다.");
				case 401, 403 -> new ApiException(
						RIOT_API_AUTH_FAILED,
						"Riot API 키가 유효하지 않거나 만료되었습니다.");
				case 429 -> new ApiException(
						RIOT_API_RATE_LIMITED,
						"Riot API 요청 한도를 초과했습니다. 잠시 후 다시 시도해 주세요.");
				default -> new ApiException(
						RIOT_API_UNAVAILABLE,
						"Riot API에 일시적으로 연결할 수 없습니다.");
			};
		} catch (ResourceAccessException exception) {
			throw new ApiException(RIOT_API_UNAVAILABLE, "Riot API 응답 시간이 초과되었습니다.");
		}
	}

	public RiotProfileLookup fetchProfile(String gameName, String tagLine) {
		RiotAccountLookup account = fetchAccount(gameName, tagLine);
		RiotRankLookup rank = fetchRank(account.puuid());

		Lane[] lanes = fetchLanePreferences(account.puuid());
		return new RiotProfileLookup(account, rank.summoner(), rank.soloRank(), lanes[0], lanes[1]);
	}

	public RiotRankLookup fetchRank(String puuid) {
		SummonerLookup summoner = execute(platformClient, "/lol/summoner/v4/summoners/by-puuid/{puuid}",
				SummonerLookup.class, puuid);
		LeagueEntry[] entries = execute(platformClient,
				"/lol/league/v4/entries/by-puuid/{puuid}", LeagueEntry[].class, puuid);
		LeagueEntry solo = entries == null ? null : List.of(entries).stream()
				.filter(entry -> "RANKED_SOLO_5x5".equals(entry.queueType()))
				.findFirst()
				.orElse(null);
		return new RiotRankLookup(summoner, solo);
	}

	private Lane[] fetchLanePreferences(String puuid) {
		String[] matchIds = execute(regionalClient,
				"/lol/match/v5/matches/by-puuid/{puuid}/ids?queue=420&start=0&count=10",
				String[].class,
				puuid);
		if (matchIds == null || matchIds.length == 0) {
			return new Lane[] {null, null};
		}
		Map<Lane, Integer> counts = new EnumMap<>(Lane.class);
		for (String matchId : matchIds) {
			MatchLookup match = execute(regionalClient, "/lol/match/v5/matches/{matchId}", MatchLookup.class, matchId);
			if (match == null || match.info() == null || match.info().participants() == null) {
				continue;
			}
			match.info().participants().stream()
					.filter(participant -> puuid.equals(participant.puuid()))
					.findFirst()
					.map(this::toLane)
					.ifPresent(lane -> counts.merge(lane, 1, Integer::sum));
		}
		List<Lane> sorted = new ArrayList<>(counts.keySet());
		sorted.sort(Comparator.<Lane>comparingInt(counts::get).reversed().thenComparing(Enum::ordinal));
		return new Lane[] {
				sorted.isEmpty() ? null : sorted.get(0),
				sorted.size() < 2 ? null : sorted.get(1)
		};
	}

	private Lane toLane(MatchParticipant participant) {
		String position = participant.teamPosition();
		if (position == null || position.isBlank()) {
			position = participant.individualPosition();
		}
		return switch (position == null ? "" : position) {
			case "TOP" -> Lane.TOP;
			case "JUNGLE" -> Lane.JUNGLE;
			case "MIDDLE" -> Lane.MID;
			case "BOTTOM" -> Lane.ADC;
			case "UTILITY" -> Lane.SUPPORT;
			default -> null;
		};
	}

	private <T> T execute(RestClient client, String uri, Class<T> responseType, Object... variables) {
		requireApiKey();
		try {
			return client.get()
					.uri(uri, variables)
					.header(RIOT_TOKEN_HEADER, apiKey)
					.header(HttpHeaders.ACCEPT, "application/json")
					.retrieve()
					.body(responseType);
		} catch (RestClientResponseException exception) {
			throw mapException(exception);
		} catch (ResourceAccessException exception) {
			throw new ApiException(RIOT_API_UNAVAILABLE, "Riot API 응답 시간이 초과되었습니다.");
		}
	}

	private void requireApiKey() {
		if (apiKey == null || apiKey.isBlank()) {
			throw new ApiException(RIOT_API_KEY_MISSING, "Riot API 키가 설정되지 않았습니다. RIOT_API_KEY를 설정해 주세요.");
		}
	}

	private ApiException mapException(RestClientResponseException exception) {
		return switch (exception.getStatusCode().value()) {
			case 404 -> new ApiException(RIOT_ACCOUNT_NOT_FOUND, "Riot 데이터를 찾을 수 없습니다.");
			case 401, 403 -> new ApiException(RIOT_API_AUTH_FAILED, "Riot API 키가 유효하지 않거나 만료되었습니다.");
			case 429 -> new ApiException(RIOT_API_RATE_LIMITED, "Riot API 요청 한도를 초과했습니다. 잠시 후 다시 시도해 주세요.");
			default -> new ApiException(RIOT_API_UNAVAILABLE, "Riot API에 일시적으로 연결할 수 없습니다.");
		};
	}

	public record RiotAccountLookup(String puuid, String gameName, String tagLine) {
	}

	public record SummonerLookup(String id, Integer profileIconId, Integer summonerLevel) {
	}

	public record LeagueEntry(String queueType, String tier, String rank, int leaguePoints, int wins, int losses) {
	}

	public record RiotProfileLookup(
			RiotAccountLookup account,
			SummonerLookup summoner,
			LeagueEntry soloRank,
			Lane primaryLane,
			Lane secondaryLane) {
	}

	public record RiotRankLookup(SummonerLookup summoner, LeagueEntry soloRank) {
	}

	public record MatchLookup(MatchInfo info) {
	}

	public record MatchInfo(List<MatchParticipant> participants) {
	}

	public record MatchParticipant(String puuid, String teamPosition, String individualPosition) {
	}
}
