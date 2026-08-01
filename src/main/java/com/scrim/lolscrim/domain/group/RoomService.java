package com.scrim.lolscrim.domain.group;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.scrim.lolscrim.domain.group.dto.ChangeMemberRoleRequest;
import com.scrim.lolscrim.domain.group.dto.CreateRoomRequest;
import com.scrim.lolscrim.domain.group.dto.GroupUserResponse;
import com.scrim.lolscrim.domain.group.dto.PublicRoomResponse;
import com.scrim.lolscrim.domain.group.dto.RoomMemberResponse;
import com.scrim.lolscrim.domain.group.dto.RoomResponse;
import com.scrim.lolscrim.domain.group.dto.UpdateRoomRequest;
import com.scrim.lolscrim.domain.group.dto.JoinRoomRequest;
import com.scrim.lolscrim.domain.user.User;
import com.scrim.lolscrim.domain.user.UserRepository;
import com.scrim.lolscrim.domain.user.UserStatus;
import com.scrim.lolscrim.domain.session.PlayerRepository;
import com.scrim.lolscrim.domain.session.Player;
import com.scrim.lolscrim.domain.player.RiotAccount;
import com.scrim.lolscrim.domain.player.RiotAccountRepository;
import com.scrim.lolscrim.domain.player.RiotRankSnapshot;
import com.scrim.lolscrim.domain.player.RiotRankSnapshotRepository;
import com.scrim.lolscrim.domain.player.PlayerRatingRepository;
import com.scrim.lolscrim.domain.player.dto.RiotPlayerResponse;
import com.scrim.lolscrim.global.error.ApiException;
import com.scrim.lolscrim.global.error.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RoomService {

	private static final String PUBLIC_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
	private static final int PUBLIC_CODE_LENGTH = 8;
	private static final SecureRandom SECURE_RANDOM = new SecureRandom();

	private final RoomRepository roomRepository;
	private final RoomMembershipRepository membershipRepository;
	private final RoomCaptainInvitationRepository invitationRepository;
	private final GuestSessionRepository guestSessionRepository;
	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final PlayerRepository playerRepository;
	private final RiotAccountRepository riotAccountRepository;
	private final RiotRankSnapshotRepository rankRepository;
	private final PlayerRatingRepository ratingRepository;

	@Transactional
	public RoomResponse createRoom(Long ownerUserId, CreateRoomRequest request) {
		User owner = requireActiveUser(ownerUserId);
		User invitee = request.opponentCaptainUserId() == null
				? null
				: requireActiveUser(request.opponentCaptainUserId());
		if (invitee != null && owner.getId().equals(invitee.getId())) {
			throw new ApiException(
					ErrorCode.VALIDATION_ERROR,
					"상대 팀장은 그룹 생성자와 다른 사용자여야 합니다.");
		}

		PasswordValue password = encodeEntryPassword(request.entryPassword());
		LocalDateTime now = LocalDateTime.now();
		Room room = roomRepository.save(Room.create(
				ownerUserId,
				request.name().trim(),
				normalizeDescription(request.description()),
				generatePublicCode(),
				password.hash(),
				password.hint(),
				request.guestAdmissionEnabled(),
				now));

		membershipRepository.save(RoomMembership.create(
				room.getId(),
				ownerUserId,
				GroupRole.GROUP_OWNER,
				now));
		ensureMemberPlayer(room.getId(), owner, now);
		RoomCaptainInvitation invitation = invitee == null ? null : invitationRepository.save(RoomCaptainInvitation.create(
				room.getId(), invitee.getId(), ownerUserId, now, now.plusDays(7)));

		return toResponse(
				room, ownerUserId, owner, null, invitation == null ? null : invitation.getStatus(), now);
	}

	@Transactional
	public RoomResponse joinRoom(Long userId, String publicCode, JoinRoomRequest request) {
		User user = requireActiveUser(userId);
		Room room = roomRepository.findByPublicCodeAndStatus(publicCode.toUpperCase(), RoomStatus.ACTIVE)
				.orElseThrow(() -> new ApiException(ErrorCode.ROOM_NOT_FOUND, "그룹을 찾을 수 없습니다."));
		if (!room.isGuestAdmissionEnabled()) {
			throw new ApiException(ErrorCode.GUEST_ADMISSION_DISABLED, "현재 신규 참가를 받지 않는 그룹입니다.");
		}
		if (room.getEntryCodeHash() != null
				&& (request.entryPassword() == null
						|| !passwordEncoder.matches(request.entryPassword(), room.getEntryCodeHash()))) {
			throw new ApiException(ErrorCode.GUEST_ENTRY_PASSWORD_INVALID, "입장 암호가 올바르지 않습니다.");
		}
		LocalDateTime now = LocalDateTime.now();
		RoomMembership membership = membershipRepository.findByRoomIdAndUserId(room.getId(), userId)
				.orElseGet(() -> RoomMembership.create(room.getId(), userId, GroupRole.GROUP_MEMBER, now));
		membership.activate(GroupRole.GROUP_MEMBER, now);
		membershipRepository.save(membership);
		ensureMemberPlayer(room.getId(), user, now);
		return toResponse(room, userId, null, null, null, now);
	}

	void ensureMemberPlayer(Long roomId, User user, LocalDateTime now) {
		Player player = playerRepository.findByRoomIdAndMemberUserId(roomId, user.getId()).orElse(null);
		if (player == null && (user.getRiotAccountId() == null || user.getRiotAccountId() <= 0)) {
			return;
		}
		if (player == null && user.getRiotAccountId() != null) {
			Player riotPlayer = playerRepository.findByRoomIdAndRiotAccountId(roomId, user.getRiotAccountId()).orElse(null);
			if (riotPlayer != null) {
				riotPlayer.attachMember(user.getId(), user.getDisplayName(), now);
				return;
			}
		}
		if (player == null) {
			player = playerRepository.save(Player.fromMember(roomId, user.getId(), user.getDisplayName(), user.getId(), now));
		}
		player.refreshDisplayName(user.getDisplayName(), now);
		if (user.getRiotAccountId() != null) {
			player.attachRiotAccount(user.getRiotAccountId(), now);
		}
	}

	@Transactional(readOnly = true)
	public List<RoomResponse> getMyRooms(Long userId) {
		requireActiveUser(userId);
		List<Long> roomIds = membershipRepository.findActiveRoomIdsByUserId(userId);
		if (roomIds.isEmpty()) {
			return List.of();
		}
		LocalDateTime now = LocalDateTime.now();
		return roomRepository.findAllByIdInOrderByCreatedAtDesc(roomIds).stream()
				.filter(room -> room.getStatus() == RoomStatus.ACTIVE)
				.map(room -> toResponse(room, userId, null, null, null, now))
				.toList();
	}

	@Transactional(readOnly = true)
	public RoomResponse getRoom(Long userId, Long roomId) {
		Room room = requireRoom(roomId);
		requireMembership(roomId, userId);
		return toResponse(room, userId, null, null, null, LocalDateTime.now());
	}

	@Transactional
	public RoomResponse updateRoom(Long userId, Long roomId, UpdateRoomRequest request) {
		Room room = requireRoom(roomId);
		requireManager(room, userId);
		if (request.name() != null && request.name().isBlank()) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR, "그룹 이름은 공백일 수 없습니다.");
		}

		boolean updateEntryPassword = request.entryPassword() != null;
		PasswordValue password = updateEntryPassword
				? encodeEntryPassword(request.entryPassword())
				: PasswordValue.unchanged();
		LocalDateTime now = LocalDateTime.now();
		room.update(
				request.name() == null ? null : request.name().trim(),
				request.description(),
				request.guestAdmissionEnabled(),
				password.hash(),
				password.hint(),
				updateEntryPassword,
				now);
		return toResponse(room, userId, null, null, null, now);
	}

	@Transactional
	public RoomResponse rotatePublicCode(Long userId, Long roomId) {
		Room room = requireRoom(roomId);
		requireManager(room, userId);
		LocalDateTime now = LocalDateTime.now();
		room.rotatePublicCode(generatePublicCode(), now);
		return toResponse(room, userId, null, null, null, now);
	}

	@Transactional(readOnly = true)
	public List<RoomMemberResponse> getMembers(Long userId, Long roomId) {
		requireRoom(roomId);
		requireMembership(roomId, userId);
		return membershipRepository.findAllByRoomIdAndActiveTrueOrderByJoinedAtAsc(roomId)
				.stream()
				.map(membership -> {
					User member = userRepository.findById(membership.getUserId())
							.orElseThrow(() -> new ApiException(
									ErrorCode.USER_NOT_FOUND,
									"그룹 회원 계정을 찾을 수 없습니다."));
					return new RoomMemberResponse(
							membership.getId(),
							GroupUserResponse.from(member),
							membership.getRole(),
							membership.getJoinedAt(),
							linkedPlayer(roomId, member.getId()));
				})
				.toList();
	}

	@Transactional
	public RoomMemberResponse changeMemberRole(
			Long ownerUserId,
			Long roomId,
			Long memberUserId,
			ChangeMemberRoleRequest request) {
		Room room = requireRoom(roomId);
		if (!room.getOwnerUserId().equals(ownerUserId)) {
			throw new ApiException(
					ErrorCode.ROOM_MANAGEMENT_DENIED,
					"그룹 소유자만 관리자 역할을 변경할 수 있습니다.");
		}
		if (request.role() == GroupRole.GROUP_OWNER || room.getOwnerUserId().equals(memberUserId)) {
			throw new ApiException(
					ErrorCode.VALIDATION_ERROR,
					"그룹 소유자 역할은 이 API로 변경할 수 없습니다.");
		}
		RoomMembership membership = requireMembership(roomId, memberUserId);
		membership.changeRole(request.role(), LocalDateTime.now());
		User member = requireActiveUser(memberUserId);
		return new RoomMemberResponse(
				membership.getId(),
				GroupUserResponse.from(member),
				membership.getRole(),
				membership.getJoinedAt(),
				linkedPlayer(roomId, memberUserId));
	}

	@Transactional
	public void removeMember(Long actorUserId, Long roomId, Long memberUserId) {
		Room room = requireRoom(roomId);
		requireManager(room, actorUserId);
		if (room.getOwnerUserId().equals(memberUserId)) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR, "그룹 소유자는 삭제할 수 없습니다.");
		}
		RoomMembership target = requireMembership(roomId, memberUserId);
		if (!room.getOwnerUserId().equals(actorUserId) && target.getRole() == GroupRole.GROUP_MANAGER) {
			throw new ApiException(ErrorCode.ROOM_MANAGEMENT_DENIED, "관리자는 다른 관리자를 삭제할 수 없습니다.");
		}
		LocalDateTime now = LocalDateTime.now();
		target.deactivate(now);
		playerRepository.findByRoomIdAndMemberUserId(roomId, memberUserId)
				.ifPresent(player -> player.deactivate(now));
	}

	@Transactional
	public void leaveRoom(Long userId, Long roomId) {
		Room room = requireRoom(roomId);
		if (room.getOwnerUserId().equals(userId)) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR, "그룹 소유자는 탈퇴 대신 그룹을 삭제해야 합니다.");
		}
		RoomMembership membership = requireMembership(roomId, userId);
		LocalDateTime now = LocalDateTime.now();
		membership.deactivate(now);
		playerRepository.findByRoomIdAndMemberUserId(roomId, userId)
				.ifPresent(player -> player.deactivate(now));
	}

	@Transactional
	public void deleteRoom(Long userId, Long roomId) {
		Room room = requireRoom(roomId);
		if (!room.getOwnerUserId().equals(userId)) {
			throw new ApiException(ErrorCode.ROOM_MANAGEMENT_DENIED, "그룹 소유자만 그룹을 삭제할 수 있습니다.");
		}
		room.archive(LocalDateTime.now());
	}

	private RiotPlayerResponse linkedPlayer(Long roomId, Long memberUserId) {
		Player player = playerRepository.findByRoomIdAndMemberUserId(roomId, memberUserId)
				.filter(Player::isActive)
				.filter(value -> value.getRiotAccountId() != null)
				.orElse(null);
		if (player == null) {
			return null;
		}
		RiotAccount account = riotAccountRepository.findById(player.getRiotAccountId()).orElse(null);
		if (account == null) {
			return null;
		}
		RiotRankSnapshot rank = rankRepository
				.findFirstByRiotAccountIdAndQueueTypeOrderByCapturedAtDesc(account.getId(), "RANKED_SOLO_5x5")
				.orElse(null);
		return RiotPlayerResponse.from(player, account, rank, ratingRepository.findById(player.getId()).orElse(null));
	}

	@Transactional(readOnly = true)
	public PublicRoomResponse getPublicRoom(String publicCode) {
		Room room = roomRepository.findByPublicCodeAndStatus(publicCode, RoomStatus.ACTIVE)
				.orElseThrow(() -> new ApiException(ErrorCode.ROOM_NOT_FOUND, "그룹을 찾을 수 없습니다."));
		return toPublicResponse(room, LocalDateTime.now());
	}

	Room requireRoom(Long roomId) {
		return roomRepository.findById(roomId)
				.filter(room -> room.getStatus() == RoomStatus.ACTIVE)
				.orElseThrow(() -> new ApiException(ErrorCode.ROOM_NOT_FOUND, "그룹을 찾을 수 없습니다."));
	}

	RoomMembership requireMembership(Long roomId, Long userId) {
		return membershipRepository.findByRoomIdAndUserIdAndActiveTrue(roomId, userId)
				.orElseThrow(() -> new ApiException(
						ErrorCode.ROOM_ACCESS_DENIED,
						"이 그룹에 접근할 권한이 없습니다."));
	}

	void requireManager(Room room, Long userId) {
		RoomMembership membership = requireMembership(room.getId(), userId);
		if (membership.getRole() != GroupRole.GROUP_OWNER
				&& membership.getRole() != GroupRole.GROUP_MANAGER) {
			throw new ApiException(
					ErrorCode.ROOM_MANAGEMENT_DENIED,
					"그룹 관리 권한이 필요합니다.");
		}
	}

	PublicRoomResponse toPublicResponse(Room room, LocalDateTime now) {
		return new PublicRoomResponse(
				room.getId(),
				room.getName(),
				room.getDescription(),
				room.getPublicCode(),
				room.isGuestAdmissionEnabled(),
				room.getEntryCodeHash() != null,
				participantCount(room.getId(), now));
	}

	private RoomResponse toResponse(
			Room room,
			Long viewerUserId,
			User knownOwner,
			User knownOpponent,
			CaptainInvitationStatus knownInvitationStatus,
			LocalDateTime now) {
		User owner = knownOwner != null ? knownOwner : requireUser(room.getOwnerUserId());
		User opponent = knownOpponent;
		if (opponent == null && room.getOpponentCaptainUserId() != null) {
			opponent = requireUser(room.getOpponentCaptainUserId());
		}
		CaptainInvitationStatus invitationStatus = knownInvitationStatus;
		if (invitationStatus == null) {
			invitationStatus = invitationRepository.findFirstByRoomIdOrderByCreatedAtDesc(room.getId())
					.map(invitation -> {
						if (invitation.getStatus() == CaptainInvitationStatus.PENDING
								&& invitation.isExpired(now)) {
							return CaptainInvitationStatus.EXPIRED;
						}
						return invitation.getStatus();
					})
					.orElse(null);
		}
		GroupRole myRole = membershipRepository
				.findByRoomIdAndUserIdAndActiveTrue(room.getId(), viewerUserId)
				.map(RoomMembership::getRole)
				.orElse(null);
		return new RoomResponse(
				room.getId(),
				room.getName(),
				room.getDescription(),
				room.getPublicCode(),
				room.isGuestAdmissionEnabled(),
				room.getEntryCodeHash() != null,
				room.getStatus(),
				participantCount(room.getId(), now),
				roomRepository.countSessions(room.getId()),
				roomRepository.countMatches(room.getId()),
				GroupUserResponse.from(owner),
				opponent == null ? null : GroupUserResponse.from(opponent),
				invitationStatus,
				myRole,
				room.getCreatedAt());
	}

	private long participantCount(Long roomId, LocalDateTime now) {
		return membershipRepository.countByRoomIdAndActiveTrue(roomId)
				+ guestSessionRepository.countActiveByRoomId(roomId, now)
				+ playerRepository
						.countByRoomIdAndRiotAccountIdIsNotNullAndMemberUserIdIsNullAndGuestSessionIdIsNullAndActiveTrue(
								roomId);
	}

	private User requireActiveUser(Long userId) {
		User user = requireUser(userId);
		if (user.getStatus() != UserStatus.ACTIVE) {
			throw new ApiException(HttpStatus.FORBIDDEN, "이용할 수 없는 계정입니다.");
		}
		return user;
	}

	private User requireUser(Long userId) {
		return userRepository.findById(userId)
				.orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND, "사용자를 찾을 수 없습니다."));
	}

	private String generatePublicCode() {
		for (int attempt = 0; attempt < 32; attempt++) {
			StringBuilder code = new StringBuilder(PUBLIC_CODE_LENGTH);
			for (int i = 0; i < PUBLIC_CODE_LENGTH; i++) {
				code.append(PUBLIC_CODE_ALPHABET.charAt(
						SECURE_RANDOM.nextInt(PUBLIC_CODE_ALPHABET.length())));
			}
			String candidate = code.toString();
			if (!roomRepository.existsByPublicCode(candidate)) {
				return candidate;
			}
		}
		throw new ApiException(
				ErrorCode.DATA_CONFLICT,
				"공개 초대 코드를 생성하지 못했습니다. 다시 시도해 주세요.");
	}

	private PasswordValue encodeEntryPassword(String password) {
		if (password == null || password.isBlank()) {
			return new PasswordValue(null, null);
		}
		if (password.length() < 4) {
			throw new ApiException(
					ErrorCode.VALIDATION_ERROR,
					"입장 암호는 4자 이상이어야 합니다.");
		}
		String hint = password.substring(0, 1) + "**" + password.substring(password.length() - 1);
		return new PasswordValue(passwordEncoder.encode(password), hint);
	}

	private static String normalizeDescription(String description) {
		return description == null || description.isBlank() ? null : description.trim();
	}

	private record PasswordValue(String hash, String hint) {

		static PasswordValue unchanged() {
			return new PasswordValue(null, null);
		}
	}
}
