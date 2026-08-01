package com.scrim.lolscrim.domain.player;

import static com.scrim.lolscrim.global.error.ErrorCode.PLAYER_ALREADY_EXISTS;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.scrim.lolscrim.domain.group.GroupRole;
import com.scrim.lolscrim.domain.group.RoomMembership;
import com.scrim.lolscrim.domain.group.RoomMembershipRepository;
import com.scrim.lolscrim.domain.group.RoomRepository;
import com.scrim.lolscrim.domain.player.RiotProfileSyncService.SyncedRiotProfile;
import com.scrim.lolscrim.domain.player.dto.AddRiotPlayerRequest;
import com.scrim.lolscrim.domain.player.dto.RiotPlayerResponse;
import com.scrim.lolscrim.domain.session.Player;
import com.scrim.lolscrim.domain.session.PlayerRepository;
import com.scrim.lolscrim.global.error.ApiException;
import com.scrim.lolscrim.global.error.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RiotPlayerService {

	private final RiotProfileSyncService profileSyncService;
	private final RiotAccountRepository riotAccountRepository;
	private final PlayerRepository playerRepository;
	private final PlayerRatingRepository ratingRepository;
	private final RiotRankSnapshotRepository rankRepository;
	private final RoomRepository roomRepository;
	private final RoomMembershipRepository membershipRepository;
	private final Clock clock;

	@Transactional(readOnly = true)
	public List<RiotPlayerResponse> getPlayers(Long userId, Long roomId) {
		requireRoomMember(userId, roomId);
		List<Player> players = playerRepository
				.findAllByRoomIdAndRiotAccountIdIsNotNullAndActiveTrueOrderByCreatedAtAsc(roomId);
		Map<Long, RiotAccount> accounts = new HashMap<>();
		riotAccountRepository.findAllById(players.stream().map(Player::getRiotAccountId).toList())
				.forEach(account -> accounts.put(account.getId(), account));
		Map<Long, PlayerRating> ratings = new HashMap<>();
		ratingRepository.findAllByPlayerIdIn(players.stream().map(Player::getId).toList())
				.forEach(rating -> ratings.put(rating.getPlayerId(), rating));
		return players.stream()
				.filter(player -> accounts.containsKey(player.getRiotAccountId()))
				.map(player -> RiotPlayerResponse.from(
						player,
						accounts.get(player.getRiotAccountId()),
						rankRepository.findFirstByRiotAccountIdAndQueueTypeOrderByCapturedAtDesc(
								player.getRiotAccountId(), "RANKED_SOLO_5x5").orElse(null),
						ratings.get(player.getId())))
				.toList();
	}

	@Transactional
	public RiotPlayerResponse addPlayer(Long userId, Long roomId, AddRiotPlayerRequest request) {
		requireManager(userId, roomId);
		String gameName = request.gameName().trim();
		String tagLine = request.tagLine().trim();
		SyncedRiotProfile synced = profileSyncService.sync(gameName, tagLine);
		LocalDateTime now = LocalDateTime.now(clock);
		RiotAccount account = synced.account();

		Player existing = playerRepository.findByRoomIdAndRiotAccountId(roomId, account.getId())
				.orElse(null);
		if (existing != null && existing.isActive()) {
			throw new ApiException(PLAYER_ALREADY_EXISTS, "이미 이 그룹에 등록된 Riot ID입니다.");
		}
		Player player = existing == null
				? playerRepository.save(Player.fromRiotAccount(
						roomId, account.getId(), account.getGameName(), userId, now))
				: existing;
		if (existing != null) {
			player.refreshDisplayName(account.getGameName(), now);
		}
		PlayerRating rating = ratingRepository.findById(player.getId())
				.orElseGet(() -> ratingRepository.save(PlayerRating.initial(player.getId(), roomId, now)));
		return RiotPlayerResponse.from(player, account, synced.rank(), rating);
	}

	public List<RiotPlayerResponse> syncPlayers(Long userId, Long roomId) {
		requireManager(userId, roomId);
		List<Long> accountIds = playerRepository
				.findAllByRoomIdAndRiotAccountIdIsNotNullAndActiveTrueOrderByCreatedAtAsc(roomId)
				.stream()
				.map(Player::getRiotAccountId)
				.distinct()
				.toList();
		for (Long accountId : accountIds) {
			profileSyncService.syncRank(accountId);
		}
		return getPlayers(userId, roomId);
	}

	@Transactional
	public RiotPlayerResponse renamePlayer(Long userId, Long roomId, Long playerId, String displayName) {
		requireManager(userId, roomId);
		if (displayName == null || displayName.isBlank() || displayName.trim().length() > 50) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR, "참가자 이름은 1~50자여야 합니다.");
		}
		Player player = requireStandalonePlayer(roomId, playerId);
		player.refreshDisplayName(displayName.trim(), LocalDateTime.now(clock));
		RiotAccount account = riotAccountRepository.findById(player.getRiotAccountId())
				.orElseThrow(() -> new ApiException(ErrorCode.RIOT_ACCOUNT_NOT_FOUND, "Riot 계정을 찾을 수 없습니다."));
		return RiotPlayerResponse.from(
				player,
				account,
				rankRepository.findFirstByRiotAccountIdAndQueueTypeOrderByCapturedAtDesc(
						account.getId(), "RANKED_SOLO_5x5").orElse(null),
				ratingRepository.findById(playerId).orElse(null));
	}

	@Transactional
	public void removePlayer(Long userId, Long roomId, Long playerId) {
		requireManager(userId, roomId);
		requireStandalonePlayer(roomId, playerId).deactivate(LocalDateTime.now(clock));
	}

	private Player requireStandalonePlayer(Long roomId, Long playerId) {
		return playerRepository.findById(playerId)
				.filter(player -> player.getRoomId().equals(roomId))
				.filter(player -> player.getMemberUserId() == null && player.getGuestSessionId() == null)
				.orElseThrow(() -> new ApiException(ErrorCode.PLAYER_NOT_FOUND, "참가자를 찾을 수 없습니다."));
	}

	private RoomMembership requireRoomMember(Long userId, Long roomId) {
		if (!roomRepository.existsById(roomId)) {
			throw new ApiException(ErrorCode.ROOM_NOT_FOUND, "그룹을 찾을 수 없습니다.");
		}
		return membershipRepository.findByRoomIdAndUserIdAndActiveTrue(roomId, userId)
				.orElseThrow(() -> new ApiException(
						ErrorCode.ROOM_ACCESS_DENIED,
						"이 그룹의 참가자를 조회할 권한이 없습니다."));
	}

	private void requireManager(Long userId, Long roomId) {
		RoomMembership membership = requireRoomMember(userId, roomId);
		if (membership.getRole() != GroupRole.GROUP_OWNER
				&& membership.getRole() != GroupRole.GROUP_MANAGER) {
			throw new ApiException(
					ErrorCode.ROOM_MANAGEMENT_DENIED,
					"Riot ID 참가자는 그룹 관리자만 추가할 수 있습니다.");
		}
	}
}
