package com.scrim.lolscrim.domain.champion;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.scrim.lolscrim.domain.champion.ChampionSnapshot.ChampionData;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ChampionSyncWriter {

	private final ChampionRepository championRepository;

	@Transactional
	public ChampionSyncResult apply(ChampionSnapshot snapshot) {
		long incomingCount = snapshot.champions().size();
		if (championRepository.countByEnabledTrue() == incomingCount
				&& championRepository.countByEnabledTrueAndDdragonVersion(snapshot.version()) == incomingCount) {
			return ChampionSyncResult.skipped(snapshot.version(), (int) incomingCount);
		}

		List<Champion> existingChampions = championRepository.findAll();
		Map<Integer, Champion> existingById = new HashMap<>();
		for (Champion champion : existingChampions) {
			existingById.put(champion.getId(), champion);
		}

		Set<Integer> incomingIds = new HashSet<>();
		int created = 0;
		int updated = 0;
		for (ChampionData data : snapshot.champions()) {
			incomingIds.add(data.id());
			Champion existing = existingById.get(data.id());
			if (existing == null) {
				championRepository.save(Champion.create(snapshot.version(), data));
				created++;
			} else {
				existing.update(snapshot.version(), data);
				updated++;
			}
		}

		int disabled = 0;
		for (Champion existing : existingChampions) {
			if (existing.isEnabled() && !incomingIds.contains(existing.getId())) {
				existing.disable();
				disabled++;
			}
		}
		return new ChampionSyncResult(snapshot.version(), created, updated, disabled, false);
	}

	public record ChampionSyncResult(
			String version,
			int created,
			int updated,
			int disabled,
			boolean skipped) {

		private static ChampionSyncResult skipped(String version, int activeCount) {
			return new ChampionSyncResult(version, 0, activeCount, 0, true);
		}
	}
}
