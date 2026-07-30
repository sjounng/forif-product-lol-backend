package com.scrim.lolscrim.domain.player;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.scrim.lolscrim.domain.player.RatingSeedCalculator.Seed;
import com.scrim.lolscrim.domain.player.dto.AddPlayerRequest;
import com.scrim.lolscrim.domain.player.dto.PlayerResponse;
import com.scrim.lolscrim.domain.player.dto.PlayerResponse.RiotAccountSummary;
import com.scrim.lolscrim.domain.riot.LaneHistoryResult;
import com.scrim.lolscrim.domain.riot.QueueType;
import com.scrim.lolscrim.domain.riot.RiotAccount;
import com.scrim.lolscrim.domain.riot.RiotAccountRepository;
import com.scrim.lolscrim.domain.riot.RiotAccountService;
import com.scrim.lolscrim.domain.riot.RiotMatchService;
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
	private final PlayerLaneRatingRepository playerLaneRatingRepository;
	private final RiotAccountService riotAccountService;
	private final RiotMatchService riotMatchService;
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

		// 최근 솔랭 라인 분포 -> lanePool 초기 추천값. Riot 조회 실패해도 등록은 계속한다.
		LaneHistoryResult laneHistory = riotAccountId != null
				? riotMatchService.fetchRecentLaneHistory(puuidOf(riotAccountId))
				: LaneHistoryResult.empty(RiotSyncStatus.NOT_FOUND);
		Map<Lane, Integer> lanePool = LaneProficiencyCalculator.recommend(laneHistory.laneGames());

		Player player = Player.create(room.getId(), riotAccountId, request.displayName(), ownerUserId);
		playerRepository.save(player);

		PlayerRating rating = PlayerRating.seed(player.getId(), room.getId(), seed.rating(), seed.rd(), seedSource);
		playerRatingRepository.save(rating);

		// 라인별 점수는 전체 시드와 같은 값에서 출발한다 (실측이 쌓이면 §4.2로 갈라진다)
		lanePool.forEach((lane, proficiency) -> playerLaneRatingRepository.save(
				PlayerLaneRating.seed(player.getId(), lane, room.getId(), seed.rating(), seed.rd(), proficiency)));

		return PlayerResponse.of(player, rating, buildRiotSummary(riotAccountId), syncResult.status(),
				lanePool, laneHistory.laneGames());
	}

	@Transactional(readOnly = true)
	public List<PlayerResponse> listPlayers(Long ownerUserId, Long roomId) {
		Room room = roomService.findOwnedRoom(ownerUserId, roomId);
		List<Player> players = playerRepository.findByRoomIdAndActiveTrueOrderByDisplayNameAsc(room.getId());
		if (players.isEmpty()) {
			return List.of();
		}

		Map<Long, List<PlayerLaneRating>> laneRatingsByPlayer = playerLaneRatingRepository
				.findByPlayerIdIn(players.stream().map(Player::getId).toList()).stream()
				.collect(Collectors.groupingBy(PlayerLaneRating::getPlayerId));

		return players.stream()
				.map(player -> {
					PlayerRating rating = playerRatingRepository.findById(player.getId())
							.orElseThrow(() -> new IllegalStateException("player_ratings 누락: " + player.getId()));
					RiotAccount account = player.getRiotAccountId() == null
							? null
							: riotAccountRepository.findById(player.getRiotAccountId()).orElse(null);
					RiotSyncStatus status = account != null ? account.getSyncStatus() : RiotSyncStatus.NOT_FOUND;
					Map<Lane, Integer> lanePool = laneRatingsByPlayer
							.getOrDefault(player.getId(), List.of()).stream()
							.collect(Collectors.toMap(
									PlayerLaneRating::getLane,
									plr -> (int) plr.getSelfProficiency(),
									(a, b) -> a,
									() -> new EnumMap<>(Lane.class)));
					// 명단 조회는 Riot 을 다시 호출하지 않는다 (레이트리밋). 등록 시점 근거는 저장하지 않으므로 비운다.
					return PlayerResponse.of(player, rating, buildRiotSummary(account), status,
							lanePool, Map.of());
				})
				.toList();
	}

	private String puuidOf(Long riotAccountId) {
		return riotAccountRepository.findById(riotAccountId)
				.map(RiotAccount::getPuuid)
				.orElseThrow(() -> new IllegalStateException("riot_accounts 누락: " + riotAccountId));
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
