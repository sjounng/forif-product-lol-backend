package com.scrim.lolscrim.domain.champion;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.scrim.lolscrim.domain.champion.dto.ChampionResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ChampionService {

	private final ChampionRepository championRepository;

	@Transactional(readOnly = true)
	public List<ChampionResponse> getActiveChampions() {
		return championRepository.findAllByEnabledTrueOrderByNameKoAsc().stream()
				.map(ChampionResponse::from)
				.toList();
	}
}
