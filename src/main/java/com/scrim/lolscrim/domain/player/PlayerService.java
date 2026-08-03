package com.scrim.lolscrim.domain.player;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

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
import com.scrim.lolscrim.domain.riot.RiotProfileFetch;
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
	private final TransactionTemplate transactionTemplate;

	/**
	 * 플레이어 등록.
	 *
	 * <b>일부러 {@code @Transactional} 을 붙이지 않았다.</b> 등록 한 건이 Riot 을 최대 24회
	 * (프로필 3 + 라인 분석 21) 호출하므로, 이걸 트랜잭션 안에서 기다리면 커넥션 풀이
	 * 네트워크 대기로 점유된다. 외부 호출은 전부 2단계에서 끝내고, 결과만 들고 들어와 저장한다.
	 */
	public PlayerResponse addPlayer(Long ownerUserId, Long roomId, AddPlayerRequest request) {
		// 1) Riot 을 부르기 전에 끝낼 수 있는 검증
		Room room = roomService.findOwnedRoom(ownerUserId, roomId);
		String[] parts = parseRiotId(request.riotId());
		rejectIfAlreadyRegistered(roomId, parts[0], parts[1]);

		// 2) 외부 HTTP — 트랜잭션 밖
		RiotProfileFetch fetch = riotAccountService.fetchProfile(parts[0], parts[1]);
		LaneHistoryResult laneHistory = fetch.isOk() && fetch.puuid() != null
				? riotMatchService.fetchRecentLaneHistory(fetch.puuid())
				: LaneHistoryResult.empty(RiotSyncStatus.NOT_FOUND);

		// 3) 저장 — 여기서만 트랜잭션을 연다
		return transactionTemplate.execute(
				status -> persistPlayer(room, ownerUserId, request, fetch, laneHistory));
	}

	private PlayerResponse persistPlayer(
			Room room, Long ownerUserId, AddPlayerRequest request,
			RiotProfileFetch fetch, LaneHistoryResult laneHistory) {
		RiotSyncResult syncResult = riotAccountService.persistProfile(fetch);
		Long riotAccountId = syncResult.status() == RiotSyncStatus.OK ? syncResult.riotAccountId() : null;

		// 캐시에 없던 계정이라 1단계에서 못 걸러낸 경우를 여기서 최종 확인한다
		if (riotAccountId != null
				&& playerRepository.existsByRoomIdAndRiotAccountId(room.getId(), riotAccountId)) {
			throw new ApiException(HttpStatus.CONFLICT, "이미 이 방에 등록된 Riot 계정입니다.");
		}

		Seed seed;
		SeedSource seedSource;
		if (syncResult.isRanked()) {
			seed = RatingSeedCalculator.fromLadderScore(syncResult.ladderScore());
			seedSource = syncResult.queueType() == QueueType.RANKED_FLEX_SR
					? SeedSource.FLEX_RANK
					: SeedSource.SOLO_RANK;
		} else {
			seed = RatingSeedCalculator.defaultSeed();
			seedSource = SeedSource.DEFAULT;
		}

		Map<Lane, Integer> lanePool = LaneProficiencyCalculator.recommend(laneHistory.laneGames());

		Player player = Player.create(room.getId(), riotAccountId, request.displayName(), ownerUserId);
		playerRepository.save(player);

		PlayerRating rating = PlayerRating.seed(player.getId(), room.getId(), seed.rating(), seed.rd(), seedSource);
		playerRatingRepository.save(rating);

		// 라인별 점수는 전체 시드에서 출발하되, RD(불확실성)는 그 라인을 얼마나 뛰었는지에 따라 갈린다.
		// 안 가본 라인까지 낮은 RD 로 두면 Glicko 가 그 라인 점수를 가장 느리게 교정한다 (DESIGN §4.1-C).
		List<PlayerLaneRating> laneRatings = lanePool.entrySet().stream()
				.map(entry -> PlayerLaneRating.seed(
						player.getId(), entry.getKey(), room.getId(),
						seed.rating(),
						RatingSeedCalculator.laneRd(seed.rd(), entry.getValue()),
						entry.getValue()))
				.toList();
		playerLaneRatingRepository.saveAll(laneRatings);

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

		// 인원수에 비례해 쿼리가 늘지 않도록 전부 일괄 조회한다 (명단은 방 진입 시 매번 뜨는 화면이다)
		List<Long> playerIds = players.stream().map(Player::getId).toList();
		Map<Long, PlayerRating> ratings = playerRatingRepository.findAllById(playerIds).stream()
				.collect(Collectors.toMap(PlayerRating::getPlayerId, Function.identity()));
		Map<Long, List<PlayerLaneRating>> laneRatingsByPlayer = playerLaneRatingRepository
				.findByPlayerIdIn(playerIds).stream()
				.collect(Collectors.groupingBy(PlayerLaneRating::getPlayerId));

		List<Long> riotAccountIds = players.stream()
				.map(Player::getRiotAccountId)
				.filter(Objects::nonNull)
				.toList();
		Map<Long, RiotAccount> accounts = riotAccountRepository.findAllById(riotAccountIds).stream()
				.collect(Collectors.toMap(RiotAccount::getId, Function.identity()));
		Map<Long, RiotRankSnapshot> latestSnapshots = latestSnapshotsByAccount(riotAccountIds);

		return players.stream()
				.map(player -> {
					PlayerRating rating = ratings.get(player.getId());
					if (rating == null) {
						throw new IllegalStateException("player_ratings 누락: " + player.getId());
					}
					RiotAccount account = player.getRiotAccountId() == null
							? null
							: accounts.get(player.getRiotAccountId());
					RiotSyncStatus status = account != null ? account.getSyncStatus() : RiotSyncStatus.NOT_FOUND;
					RiotAccountSummary summary = account == null
							? null
							: toSummary(account, latestSnapshots.get(account.getId()));
					Map<Lane, Integer> lanePool = laneRatingsByPlayer
							.getOrDefault(player.getId(), List.of()).stream()
							.collect(Collectors.toMap(
									PlayerLaneRating::getLane,
									plr -> (int) plr.getSelfProficiency(),
									(a, b) -> a,
									() -> new EnumMap<>(Lane.class)));
					// 명단 조회는 Riot 을 다시 호출하지 않는다 (레이트리밋). 등록 시점 근거는 저장하지 않으므로 비운다.
					return PlayerResponse.of(player, rating, summary, status, lanePool, Map.of());
				})
				.toList();
	}

	/** 계정별 최신 스냅샷 1건씩. 솔랭이 있으면 솔랭, 없으면 자유 랭크를 쓴다 (DESIGN §4.1). */
	private Map<Long, RiotRankSnapshot> latestSnapshotsByAccount(List<Long> riotAccountIds) {
		if (riotAccountIds.isEmpty()) {
			return Map.of();
		}
		Map<Long, RiotRankSnapshot> best = new HashMap<>();
		for (RiotRankSnapshot snapshot : riotRankSnapshotRepository.findByRiotAccountIdIn(riotAccountIds)) {
			best.merge(snapshot.getRiotAccountId(), snapshot, PlayerService::preferredSnapshot);
		}
		return best;
	}

	private static RiotRankSnapshot preferredSnapshot(RiotRankSnapshot a, RiotRankSnapshot b) {
		boolean aSolo = a.getQueueType() == QueueType.RANKED_SOLO_5x5;
		boolean bSolo = b.getQueueType() == QueueType.RANKED_SOLO_5x5;
		if (aSolo != bSolo) {
			return aSolo ? a : b;
		}
		return Comparator.comparing(RiotRankSnapshot::getCapturedAt,
				Comparator.nullsFirst(Comparator.naturalOrder())).compare(a, b) >= 0 ? a : b;
	}

	private String[] parseRiotId(String riotId) {
		String[] parts = riotId.split("#", 2);
		if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "riotId: \"GameName#TAG\" 형식이어야 합니다.");
		}
		return parts;
	}

	/**
	 * 이미 등록된 계정이면 Riot 을 부르기 전에 막는다.
	 * riot_accounts 가 전역 캐시라 대부분의 중복 시도에서 API 호출(계정당 최대 24콜)을 통째로 아낀다.
	 */
	private void rejectIfAlreadyRegistered(Long roomId, String gameName, String tagLine) {
		riotAccountService.findCachedAccount(gameName, tagLine).ifPresent(account -> {
			if (playerRepository.existsByRoomIdAndRiotAccountId(roomId, account.getId())) {
				throw new ApiException(HttpStatus.CONFLICT, "이미 이 방에 등록된 Riot 계정입니다.");
			}
		});
	}

	private RiotAccountSummary buildRiotSummary(Long riotAccountId) {
		if (riotAccountId == null) {
			return null;
		}
		RiotAccount account = riotAccountRepository.findById(riotAccountId).orElseThrow();
		return toSummary(account, riotRankSnapshotRepository
				.findFirstByRiotAccountIdAndQueueTypeOrderByCapturedAtDesc(account.getId(), QueueType.RANKED_SOLO_5x5)
				.or(() -> riotRankSnapshotRepository.findFirstByRiotAccountIdAndQueueTypeOrderByCapturedAtDesc(
						account.getId(), QueueType.RANKED_FLEX_SR))
				.orElse(null));
	}

	private static RiotAccountSummary toSummary(RiotAccount account, RiotRankSnapshot snapshot) {
		if (snapshot == null) {
			return new RiotAccountSummary(account.getGameName(), account.getTagLine(), Tier.UNRANKED, null, 0);
		}
		return new RiotAccountSummary(
				account.getGameName(), account.getTagLine(), snapshot.getTier(), snapshot.getRankDivision(),
				snapshot.getLeaguePoints());
	}
}
