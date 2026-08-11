package com.scrim.lolscrim.domain.champion;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.scrim.lolscrim.domain.champion.dto.ChampionResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/champions")
@RequiredArgsConstructor
public class ChampionController {

	private final ChampionService championService;

	@GetMapping
	public List<ChampionResponse> getChampions() {
		return championService.getActiveChampions();
	}
}
