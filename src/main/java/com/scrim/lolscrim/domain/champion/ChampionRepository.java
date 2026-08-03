package com.scrim.lolscrim.domain.champion;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ChampionRepository extends JpaRepository<Champion, Integer> {

	long countByEnabledTrue();

	long countByEnabledTrueAndDdragonVersion(String ddragonVersion);

	List<Champion> findAllByEnabledTrueOrderByNameKoAsc();
}
