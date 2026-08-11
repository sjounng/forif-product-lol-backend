package com.scrim.lolscrim.domain.user;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.scrim.lolscrim.domain.auth.UserSessionRepository;
import com.scrim.lolscrim.domain.auth.dto.UserResponse;
import com.scrim.lolscrim.domain.user.dto.ChangePasswordRequest;
import com.scrim.lolscrim.domain.user.dto.UserSearchResponse;
import com.scrim.lolscrim.domain.user.dto.UserProfileResponse;
import com.scrim.lolscrim.domain.riot.RiotAccount;
import com.scrim.lolscrim.domain.riot.RiotAccountRepository;
import com.scrim.lolscrim.domain.player.RiotProfileSyncService;
import com.scrim.lolscrim.domain.riot.RiotRankSnapshot;
import com.scrim.lolscrim.domain.riot.RiotRankSnapshotRepository;
import com.scrim.lolscrim.domain.riot.QueueType;
import com.scrim.lolscrim.domain.player.dto.AddRiotPlayerRequest;
import com.scrim.lolscrim.domain.player.dto.RiotAccountResponse;
import com.scrim.lolscrim.domain.player.RiotProfileSyncService.SyncedRiotProfile;
import com.scrim.lolscrim.domain.group.RoomMembership;
import com.scrim.lolscrim.domain.group.RoomMembershipRepository;
import com.scrim.lolscrim.domain.player.Player;
import com.scrim.lolscrim.domain.player.PlayerRepository;
import com.scrim.lolscrim.global.error.ApiException;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;

@Service
@RequiredArgsConstructor
public class UserService {

	private static final int DISPLAY_NAME_MAX_LENGTH = 50;
	private static final int AVATAR_URL_MAX_LENGTH = 255;

	private final UserRepository userRepository;
	private final UserSessionRepository userSessionRepository;
	private final PasswordEncoder passwordEncoder;
	private final RiotProfileSyncService riotProfileSyncService;
	private final RiotAccountRepository riotAccountRepository;
	private final RiotRankSnapshotRepository riotRankRepository;
	private final RoomMembershipRepository membershipRepository;
	private final PlayerRepository playerRepository;

	@Transactional(readOnly = true)
	public List<UserSearchResponse> searchActiveUsers(Long requesterId, String query) {
		if (query == null || query.trim().length() < 2) {
			return List.of();
		}
		return userRepository
				.findTop10ByStatusAndDisplayNameContainingIgnoreCaseOrderByDisplayNameAsc(
						UserStatus.ACTIVE,
						query.trim())
				.stream()
				.filter(user -> !user.getId().equals(requesterId))
				.map(UserSearchResponse::from)
				.toList();
	}

	/**
	 * 진짜 부분 수정(PATCH)이다 — 요청에 없는 필드는 그대로 둔다.
	 * 필드가 명시적으로 null이면(예: avatarUrl) 그 값을 지운다.
	 */
	@Transactional
	public UserResponse updateProfile(Long userId, JsonNode request) {
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "존재하지 않는 사용자입니다."));
		if (user.getStatus() != UserStatus.ACTIVE) {
			throw new ApiException(HttpStatus.FORBIDDEN, "이용할 수 없는 계정입니다.");
		}

		String displayName = user.getDisplayName();
		if (request.has("displayName")) {
			JsonNode node = request.get("displayName");
			String value = node.isNull() ? null : node.asString();
			if (value == null || value.isBlank()) {
				throw new ApiException(HttpStatus.BAD_REQUEST, "displayName: 공백일 수 없습니다.");
			}
			if (value.length() > DISPLAY_NAME_MAX_LENGTH) {
				throw new ApiException(HttpStatus.BAD_REQUEST,
						"displayName: " + DISPLAY_NAME_MAX_LENGTH + "자 이하여야 합니다.");
			}
			displayName = value;
		}

		String avatarUrl = user.getAvatarUrl();
		if (request.has("avatarUrl")) {
			JsonNode node = request.get("avatarUrl");
			if (node.isNull()) {
				avatarUrl = null;
			} else {
				String value = node.asString();
				if (value.length() > AVATAR_URL_MAX_LENGTH) {
					throw new ApiException(HttpStatus.BAD_REQUEST,
							"avatarUrl: " + AVATAR_URL_MAX_LENGTH + "자 이하여야 합니다.");
				}
				if (!isHttpUrl(value)) {
					throw new ApiException(HttpStatus.BAD_REQUEST, "avatarUrl: 올바른 http(s) URL 형식이 아닙니다.");
				}
				avatarUrl = value;
			}
		}

		user.updateProfile(displayName, avatarUrl);
		LocalDateTime now = LocalDateTime.now();
		String updatedDisplayName = displayName;
		for (RoomMembership membership : membershipRepository.findAllByUserIdAndActiveTrue(userId)) {
			playerRepository.findByRoomIdAndMemberUserId(membership.getRoomId(), userId)
					.ifPresent(player -> player.refreshDisplayName(updatedDisplayName, now));
		}
		return UserResponse.from(user);
	}

	@Transactional(readOnly = true)
	public UserProfileResponse getProfile(Long userId) {
		User user = requireActiveUser(userId);
		if (user.getRiotAccountId() == null) {
			return new UserProfileResponse(UserResponse.from(user), null, null, null);
		}
		RiotAccount account = riotAccountRepository.findById(user.getRiotAccountId()).orElse(null);
		if (account == null) {
			return new UserProfileResponse(UserResponse.from(user), null, null, null);
		}
		RiotRankSnapshot rank = riotRankRepository
				.findFirstByRiotAccountIdAndQueueTypeOrderByCapturedAtDesc(account.getId(), QueueType.RANKED_SOLO_5x5)
				.orElse(null);
		return new UserProfileResponse(
				UserResponse.from(user),
				RiotAccountResponse.from(account, rank),
				account.getPrimaryLane(),
				account.getSecondaryLane());
	}

	@Transactional
	public UserProfileResponse linkRiotAccount(Long userId, AddRiotPlayerRequest request) {
		User user = requireActiveUser(userId);
		SyncedRiotProfile synced = riotProfileSyncService.sync(request.gameName(), request.tagLine());
		userRepository.findByRiotAccountId(synced.account().getId())
				.filter(existing -> !existing.getId().equals(userId))
				.ifPresent(existing -> {
					throw new ApiException(HttpStatus.CONFLICT, "이미 다른 회원과 연동된 Riot 계정입니다.");
				});
		user.linkRiotAccount(synced.account().getId());
		LocalDateTime now = LocalDateTime.now();
		for (RoomMembership membership : membershipRepository.findAllByUserIdAndActiveTrue(userId)) {
			mergeRoomPlayer(membership.getRoomId(), user, synced.account(), now);
		}
		return new UserProfileResponse(
				UserResponse.from(user),
				RiotAccountResponse.from(synced.account(), synced.rank()),
				synced.account().getPrimaryLane(),
				synced.account().getSecondaryLane());
	}

	private void mergeRoomPlayer(Long roomId, User user, RiotAccount account, LocalDateTime now) {
		Player memberPlayer = playerRepository.findByRoomIdAndMemberUserId(roomId, user.getId()).orElse(null);
		Player riotPlayer = playerRepository.findByRoomIdAndRiotAccountId(roomId, account.getId()).orElse(null);
		if (memberPlayer == null && riotPlayer != null) {
			riotPlayer.attachMember(user.getId(), user.getDisplayName(), now);
			return;
		}
		if (memberPlayer == null) {
			memberPlayer = playerRepository.save(Player.fromMember(
					roomId, user.getId(), user.getDisplayName(), user.getId(), now));
		}
		if (riotPlayer != null && !riotPlayer.getId().equals(memberPlayer.getId())) {
			riotPlayer.detachRiotAccount(now);
			riotPlayer.deactivate(now);
			playerRepository.saveAndFlush(riotPlayer);
		}
		memberPlayer.attachRiotAccount(account.getId(), now);
		memberPlayer.refreshDisplayName(user.getDisplayName(), now);
	}

	private User requireActiveUser(Long userId) {
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "존재하지 않는 사용자입니다."));
		if (user.getStatus() != UserStatus.ACTIVE) {
			throw new ApiException(HttpStatus.FORBIDDEN, "이용할 수 없는 계정입니다.");
		}
		return user;
	}

	private static boolean isHttpUrl(String value) {
		try {
			URI uri = new URI(value);
			return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
					&& uri.getHost() != null;
		} catch (URISyntaxException e) {
			return false;
		}
	}

	@Transactional
	public void changePassword(Long userId, ChangePasswordRequest request) {
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "존재하지 않는 사용자입니다."));
		if (user.getStatus() != UserStatus.ACTIVE) {
			throw new ApiException(HttpStatus.FORBIDDEN, "이용할 수 없는 계정입니다.");
		}
		if (user.getPasswordHash() == null
				|| !passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "현재 비밀번호가 올바르지 않습니다.");
		}
		if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "새 비밀번호는 현재 비밀번호와 달라야 합니다.");
		}

		user.changePassword(passwordEncoder.encode(request.newPassword()));
		userSessionRepository.revokeAllByUserId(userId, LocalDateTime.now());
	}
}
