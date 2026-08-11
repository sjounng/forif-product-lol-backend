package com.scrim.lolscrim.domain.champion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.scrim.lolscrim.domain.champion.DataDragonChampionCatalog.ChampionPayload;
import com.scrim.lolscrim.domain.champion.DataDragonChampionCatalog.ImagePayload;

class ChampionCatalogMergerTest {

	private ChampionCatalogMerger merger;

	@BeforeEach
	void setUp() {
		DataDragonProperties properties = new DataDragonProperties(
				"https://ddragon.leagueoflegends.com",
				"ko_KR",
				"en_US",
				true,
				5_000,
				10_000);
		merger = new ChampionCatalogMerger(properties);
	}

	@Test
	void mergesLocalesAndBuildsImageUrl() {
		DataDragonChampionCatalog korean = catalog(
				new ChampionPayload(
						"Aatrox",
						"266",
						"아트록스",
						List.of("Fighter"),
						new ImagePayload("Aatrox.png")));
		DataDragonChampionCatalog english = catalog(
				new ChampionPayload(
						"Aatrox",
						"266",
						"Aatrox",
						List.of("Fighter"),
						new ImagePayload("Aatrox.png")));

		ChampionSnapshot snapshot = merger.merge("16.14.1", korean, english);

		assertThat(snapshot.version()).isEqualTo("16.14.1");
		assertThat(snapshot.champions()).singleElement().satisfies(champion -> {
			assertThat(champion.id()).isEqualTo(266);
			assertThat(champion.riotId()).isEqualTo("Aatrox");
			assertThat(champion.nameKo()).isEqualTo("아트록스");
			assertThat(champion.nameEn()).isEqualTo("Aatrox");
			assertThat(champion.tags()).containsExactly("Fighter");
			assertThat(champion.imageUrl()).isEqualTo(
					"https://ddragon.leagueoflegends.com/cdn/16.14.1/img/champion/Aatrox.png");
		});
	}

	@Test
	void rejectsDifferentLocaleChampionSets() {
		DataDragonChampionCatalog korean = catalog(
				new ChampionPayload(
						"Aatrox",
						"266",
						"아트록스",
						List.of("Fighter"),
						new ImagePayload("Aatrox.png")));
		DataDragonChampionCatalog english = catalog(
				new ChampionPayload(
						"Ahri",
						"103",
						"Ahri",
						List.of("Mage", "Assassin"),
						new ImagePayload("Ahri.png")));

		assertThatThrownBy(() -> merger.merge("16.14.1", korean, english))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("Data Dragon locale champion key sets do not match.");
	}

	private static DataDragonChampionCatalog catalog(ChampionPayload champion) {
		return new DataDragonChampionCatalog(Map.of(champion.id(), champion));
	}
}
