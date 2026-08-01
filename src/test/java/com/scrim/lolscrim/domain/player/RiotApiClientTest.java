package com.scrim.lolscrim.domain.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.scrim.lolscrim.global.error.ApiException;
import com.scrim.lolscrim.global.error.ErrorCode;

class RiotApiClientTest {

	@Test
	void reportsMissingApiKeyBeforeNetworkRequest() {
		RiotApiClient client = new RiotApiClient(
				RestClient.builder().baseUrl("https://asia.api.riotgames.com").build(),
				"");

		assertThatThrownBy(() -> client.fetchAccount("Hide on bush", "KR1"))
				.isInstanceOfSatisfying(ApiException.class, exception -> {
					assertThat(exception.getCode()).isEqualTo(ErrorCode.RIOT_API_KEY_MISSING);
					assertThat(exception.getMessage()).contains("RIOT_API_KEY");
				});
	}

	@Test
	void fetchesSoloRankByPuuidAfterSummonerIdRemoval() {
		RestClient.Builder platformBuilder = RestClient.builder()
				.baseUrl("https://kr.api.riotgames.com");
		MockRestServiceServer server = MockRestServiceServer.bindTo(platformBuilder).build();
		RiotApiClient client = new RiotApiClient(
				RestClient.builder().baseUrl("https://asia.api.riotgames.com").build(),
				platformBuilder.build(),
				"  RGAPI-test-key  ");

		server.expect(requestTo(
				"https://kr.api.riotgames.com/lol/summoner/v4/summoners/by-puuid/puuid-1"))
				.andExpect(header("X-Riot-Token", "RGAPI-test-key"))
				.andRespond(withSuccess(
						"{\"puuid\":\"puuid-1\",\"profileIconId\":123,\"summonerLevel\":456}",
						MediaType.APPLICATION_JSON));
		server.expect(requestTo(
				"https://kr.api.riotgames.com/lol/league/v4/entries/by-puuid/puuid-1"))
				.andExpect(header("X-Riot-Token", "RGAPI-test-key"))
				.andRespond(withSuccess(
						"[{\"queueType\":\"RANKED_SOLO_5x5\",\"tier\":\"DIAMOND\",\"rank\":\"II\",\"leaguePoints\":42,\"wins\":10,\"losses\":5}]",
						MediaType.APPLICATION_JSON));

		RiotApiClient.RiotRankLookup result = client.fetchRank("puuid-1");

		assertThat(result.soloRank()).isNotNull();
		assertThat(result.soloRank().tier()).isEqualTo("DIAMOND");
		server.verify();
	}
}
