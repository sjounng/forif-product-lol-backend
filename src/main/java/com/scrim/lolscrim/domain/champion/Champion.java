package com.scrim.lolscrim.domain.champion;

import java.time.LocalDateTime;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.scrim.lolscrim.domain.champion.ChampionSnapshot.ChampionData;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "champions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Champion {

	@Id
	@Column(name = "id", nullable = false, columnDefinition = "SMALLINT UNSIGNED")
	private Integer id;

	@Column(name = "riot_id", nullable = false, unique = true, length = 32)
	private String riotId;

	@Column(name = "name_ko", nullable = false, length = 32)
	private String nameKo;

	@Column(name = "name_en", nullable = false, length = 32)
	private String nameEn;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "tags", columnDefinition = "JSON")
	private List<String> tags;

	@Column(name = "image_url", length = 255)
	private String imageUrl;

	@Column(name = "ddragon_version", length = 16)
	private String ddragonVersion;

	@Column(name = "is_enabled", nullable = false)
	private boolean enabled;

	@Column(name = "updated_at", insertable = false, updatable = false)
	private LocalDateTime updatedAt;

	public static Champion create(String version, ChampionData data) {
		Champion champion = new Champion();
		champion.id = data.id();
		champion.update(version, data);
		return champion;
	}

	public void update(String version, ChampionData data) {
		riotId = data.riotId();
		nameKo = data.nameKo();
		nameEn = data.nameEn();
		tags = List.copyOf(data.tags());
		imageUrl = data.imageUrl();
		ddragonVersion = version;
		enabled = true;
	}

	public void disable() {
		enabled = false;
	}
}
