package com.scrim.lolscrim.domain.player;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.scrim.lolscrim.domain.player.RatingSeedCalculator.Seed;
import com.scrim.lolscrim.domain.player.dto.AddPlayerRequest;
import com.scrim.lolscrim.domain.player.dto.PlayerResponse;
import com.scrim.lolscrim.domain.player.dto.PlayerResponse.RiotAccountSummary;
import com.scrim.lolscrim.domain.riot.QueueType;
import com.scrim.lolscrim.domain.riot.RiotAccount;
import com.scrim.lolscrim.domain.riot.RiotAccountRepository;
import com.scrim.lolscrim.domain.riot.RiotAccountService;
import com.scrim.lolscrim.domain.riot.RiotRankSnapshot;
import com.scrim.lolscrim.domain.riot.RiotRankSnapshotRepository;
import com.scrim.lolscrim.domain.riot.RiotSyncResult;
import com.scrim.lolscrim.domain.riot.RiotSyncStatus;
import com.scrim.lolscrim.domain.riot.Tier;
import com.scrim.lolscrim.domain.room.Room;
import com.scrim.lolscrim.domain.room.RoomService;
import com.scrim.lolscrim.global.error.ApiException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PlayerService {

	private final RoomService roomService;
	private final PlayerRepository playerRepository;
	private final PlayerRatingRepository playerRatingRepository;
	private final RiotAccountService riotAccountService;
	private final RiotAccountRepository riotAccountRepository;
	private final RiotRankSnapshotRepository riotRankSnapshotRepository;

	@Transactional
	public PlayerResponse addPlayer(Long ownerUserId, Long roomId, AddPlayerRequest request) {
		Room room = roomService.findOwnedRoom(ownerUserId, roomId);

		String[] parts = request.riotId().split("#", 2);
		if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "riotId: \"GameName#TAG\" 형식이어야 합니다.");
		}

		RiotSyncResult syncResult = riotAccountService.syncByRiotId(parts[0], parts[1]);

		Long riotAccountId = syncResult.status() == RiotSyncStatus.OK ? syncResult.riotAccountId() : null;
		Seed seed;
		SeedSource seedSource;
		if (syncResult.isRanked()) {
			seed = RatingSeedCalculator.fromLadderScore(syncResult.ladderScore());
			seedSource = syncResult.queueType() == QueueType.RANKED_FLEX_SR ? SeedSource.FLEX_RANK : SeedSource.SOLO_RANK;
		} else {
			seed = RatingSeedCalculator.defaultSeed();
			seedSource = SeedSource.DEFAULT;
		}

		if (riotAccountId != null && playerRepository.existsByRoomIdAndRiotAccountId(roomId, riotAccountId)) {
			throw new ApiException(HttpStatus.CONFLICT, "이미 이 방에 등록된 Riot 계정입니다.");
		}

		Player player = Player.create(room.getId(), riotAccountId, request.displayName(), ownerUserId);
		playerRepository.save(player);

		PlayerRating rating = PlayerRating.seed(player.getId(), room.getId(), seed.rating(), seed.rd(), seedSource);
		playerRatingRepository.save(rating);

		return PlayerResponse.of(player, rating, buildRiotSummary(riotAccountId), syncResult.status());
	}

	@Transactional(readOnly = true)
	public List<PlayerResponse> listPlayers(Long ownerUserId, Long roomId) {
		Room room = roomService.findOwnedRoom(ownerUserId, roomId);
		return playerRepository.findByRoomIdAndActiveTrueOrderByDisplayNameAsc(room.getId()).stream()
				.map(player -> {
					PlayerRating rating = playerRatingRepository.findById(player.getId())
							.orElseThrow(() -> new IllegalStateException("player_ratings 누락: " + player.getId()));
					RiotAccount account = player.getRiotAccountId() == null
							? null
							: riotAccountRepository.findById(player.getRiotAccountId()).orElse(null);
					RiotSyncStatus status = account != null ? account.getSyncStatus() : RiotSyncStatus.NOT_FOUND;
					return PlayerResponse.of(player, rating, buildRiotSummary(account), status);
				})
				.toList();
	}

	private RiotAccountSummary buildRiotSummary(Long riotAccountId) {
		if (riotAccountId == null) {
			return null;
		}
		return buildRiotSummary(riotAccountRepository.findById(riotAccountId).orElseThrow());
	}

	private RiotAccountSummary buildRiotSummary(RiotAccount account) {
		if (account == null) {
			return null;
		}
		RiotRankSnapshot snapshot = riotRankSnapshotRepository
				.findFirstByRiotAccountIdAndQueueTypeOrderByCapturedAtDesc(account.getId(), QueueType.RANKED_SOLO_5x5)
				.or(() -> riotRankSnapshotRepository.findFirstByRiotAccountIdAndQueueTypeOrderByCapturedAtDesc(
						account.getId(), QueueType.RANKED_FLEX_SR))
				.orElse(null);
		if (snapshot == null) {
			return new RiotAccountSummary(account.getGameName(), account.getTagLine(), Tier.UNRANKED, null, 0);
		}
		return new RiotAccountSummary(
				account.getGameName(), account.getTagLine(), snapshot.getTier(), snapshot.getRankDivision(),
				snapshot.getLeaguePoints());
	}
}
