package com.scrim.lolscrim.domain.champion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class DataDragonClientTest {

	private MockRestServiceServer server;
	private DataDragonClient client;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder()
				.baseUrl("https://ddragon.example.com");
		server = MockRestServiceServer.bindTo(builder).build();
		client = new DataDragonClient(builder.build());
	}

	@Test
	void parsesLatestVersionResponse() {
		server.expect(requestTo("https://ddragon.example.com/api/versions.json"))
				.andRespond(withSuccess("[\"16.14.1\",\"16.13.1\"]", MediaType.APPLICATION_JSON));

		String version = client.fetchLatestVersion();

		assertThat(version).isEqualTo("16.14.1");
		server.verify();
	}

	@Test
	void parsesChampionCatalogResponse() {
		server.expect(requestTo(
				"https://ddragon.example.com/cdn/16.14.1/data/ko_KR/champion.json"))
				.andRespond(withSuccess("""
						{
						  "type": "champion",
						  "format": "standAloneComplex",
						  "version": "16.14.1",
						  "data": {
						    "Aatrox": {
						      "id": "Aatrox",
						      "key": "266",
						      "name": "아트록스",
						      "title": "다르킨의 검",
						      "tags": ["Fighter"],
						      "image": {
						        "full": "Aatrox.png",
						        "sprite": "champion0.png"
						      }
						    }
						  }
						}
						""", MediaType.APPLICATION_JSON));

		DataDragonChampionCatalog catalog = client.fetchChampionCatalog("16.14.1", "ko_KR");

		assertThat(catalog.data()).containsKey("Aatrox");
		assertThat(catalog.data().get("Aatrox").key()).isEqualTo("266");
		assertThat(catalog.data().get("Aatrox").name()).isEqualTo("아트록스");
		assertThat(catalog.data().get("Aatrox").tags()).containsExactly("Fighter");
		assertThat(catalog.data().get("Aatrox").image().full()).isEqualTo("Aatrox.png");
		server.verify();
	}
}
