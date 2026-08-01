package com.scrim.lolscrim.domain.group;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.scrim.lolscrim.domain.group.dto.CaptainInvitationResponse;
import com.scrim.lolscrim.domain.group.dto.GroupUserResponse;
import com.scrim.lolscrim.domain.user.User;
import com.scrim.lolscrim.domain.user.UserRepository;
import com.scrim.lolscrim.domain.user.UserStatus;
import com.scrim.lolscrim.global.error.ApiException;
import com.scrim.lolscrim.global.error.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RoomInvitationService {

	private final RoomRepository roomRepository;
	private final RoomMembershipRepository membershipRepository;
	private final RoomCaptainInvitationRepository invitationRepository;
	private final UserRepository userRepository;
	private final RoomService roomService;

	@Transactional
	public CaptainInvitationResponse invite(Long actorUserId, Long roomId, Long inviteeUserId) {
		Room room = roomService.requireRoom(roomId);
		roomService.requireManager(room, actorUserId);
		if (room.getOpponentCaptainUserId() != null) {
			throw new ApiException(
					ErrorCode.OPPONENT_CAPTAIN_ALREADY_ASSIGNED,
					"이미 상대 팀장이 확정된 그룹입니다.");
		}
		if (room.getOwnerUserId().equals(inviteeUserId)) {
			throw new ApiException(
					ErrorCode.VALIDATION_ERROR,
					"그룹 소유자를 상대 팀장으로 초대할 수 없습니다.");
		}
		if (invitationRepository.existsByRoomIdAndStatus(roomId, CaptainInvitationStatus.PENDING)) {
			throw new ApiException(
					ErrorCode.DUPLICATE_PENDING_INVITATION,
					"처리 대기 중인 상대 팀장 초대가 있습니다.");
		}

		User invitee = requireActiveUser(inviteeUserId);
		User inviter = requireActiveUser(actorUserId);
		LocalDateTime now = LocalDateTime.now();
		RoomCaptainInvitation invitation = invitationRepository.save(RoomCaptainInvitation.create(
				roomId,
				inviteeUserId,
				actorUserId,
				now,
				now.plusDays(7)));
		return toResponse(invitation, room, inviter, invitee, now);
	}

	@Transactional(readOnly = true)
	public List<CaptainInvitationResponse> getReceivedInvitations(Long userId) {
		requireActiveUser(userId);
		LocalDateTime now = LocalDateTime.now();
		return invitationRepository.findAllByInviteeUserIdOrderByCreatedAtDesc(userId)
				.stream()
				.map(invitation -> {
					Room room = roomRepository.findById(invitation.getRoomId())
							.orElseThrow(() -> new ApiException(
									ErrorCode.ROOM_NOT_FOUND,
									"초대된 그룹을 찾을 수 없습니다."));
					User inviter = requireUser(invitation.getInvitedByUserId());
					User invitee = requireUser(invitation.getInviteeUserId());
					return toResponse(invitation, room, inviter, invitee, now);
				})
				.toList();
	}

	@Transactional
	public CaptainInvitationResponse accept(Long userId, Long invitationId) {
		RoomCaptainInvitation invitation = requirePendingInvitation(userId, invitationId);
		Room room = roomService.requireRoom(invitation.getRoomId());
		if (room.getOpponentCaptainUserId() != null) {
			throw new ApiException(
					ErrorCode.OPPONENT_CAPTAIN_ALREADY_ASSIGNED,
					"이미 상대 팀장이 확정된 그룹입니다.");
		}

		LocalDateTime now = LocalDateTime.now();
		RoomMembership membership = membershipRepository
				.findByRoomIdAndUserId(room.getId(), userId)
				.orElseGet(() -> RoomMembership.create(
						room.getId(),
						userId,
						GroupRole.GROUP_MEMBER,
						now));
		membership.activate(GroupRole.GROUP_MEMBER, now);
		membershipRepository.save(membership);
		roomService.ensureMemberPlayer(room.getId(), requireUser(userId), now);
		room.assignOpponentCaptain(userId, now);
		invitation.accept(now);

		return toResponse(
				invitation,
				room,
				requireUser(invitation.getInvitedByUserId()),
				requireUser(userId),
				now);
	}

	@Transactional
	public CaptainInvitationResponse reject(Long userId, Long invitationId) {
		RoomCaptainInvitation invitation = requirePendingInvitation(userId, invitationId);
		LocalDateTime now = LocalDateTime.now();
		invitation.reject(now);
		return toResponse(
				invitation,
				roomService.requireRoom(invitation.getRoomId()),
				requireUser(invitation.getInvitedByUserId()),
				requireUser(userId),
				now);
	}

	@Transactional
	public void cancel(Long actorUserId, Long invitationId) {
		RoomCaptainInvitation invitation = invitationRepository.findByIdForUpdate(invitationId)
				.orElseThrow(() -> new ApiException(
						ErrorCode.INVITATION_NOT_FOUND,
						"초대를 찾을 수 없습니다."));
		Room room = roomService.requireRoom(invitation.getRoomId());
		roomService.requireManager(room, actorUserId);
		requirePending(invitation, LocalDateTime.now());
		invitation.cancel(LocalDateTime.now());
	}

	private RoomCaptainInvitation requirePendingInvitation(Long userId, Long invitationId) {
		RoomCaptainInvitation invitation = invitationRepository.findByIdForUpdate(invitationId)
				.orElseThrow(() -> new ApiException(
						ErrorCode.INVITATION_NOT_FOUND,
						"초대를 찾을 수 없습니다."));
		if (!invitation.getInviteeUserId().equals(userId)) {
			throw new ApiException(
					ErrorCode.INVITATION_ACCESS_DENIED,
					"이 초대를 처리할 권한이 없습니다.");
		}
		requirePending(invitation, LocalDateTime.now());
		return invitation;
	}

	private void requirePending(RoomCaptainInvitation invitation, LocalDateTime now) {
		if (invitation.getStatus() != CaptainInvitationStatus.PENDING) {
			throw new ApiException(
					ErrorCode.INVITATION_NOT_PENDING,
					"이미 처리된 초대입니다.");
		}
		if (invitation.isExpired(now)) {
			throw new ApiException(
					ErrorCode.INVITATION_EXPIRED,
					"만료된 초대입니다.");
		}
	}

	private CaptainInvitationResponse toResponse(
			RoomCaptainInvitation invitation,
			Room room,
			User inviter,
			User invitee,
			LocalDateTime now) {
		CaptainInvitationStatus status = invitation.getStatus();
		if (status == CaptainInvitationStatus.PENDING && invitation.isExpired(now)) {
			status = CaptainInvitationStatus.EXPIRED;
		}
		return new CaptainInvitationResponse(
				invitation.getId(),
				room.getId(),
				room.getName(),
				GroupUserResponse.from(inviter),
				GroupUserResponse.from(invitee),
				status,
				invitation.getExpiresAt(),
				invitation.getCreatedAt());
	}

	private User requireActiveUser(Long userId) {
		User user = requireUser(userId);
		if (user.getStatus() != UserStatus.ACTIVE) {
			throw new ApiException(ErrorCode.ACCESS_DENIED, "이용할 수 없는 계정입니다.");
		}
		return user;
	}

	private User requireUser(Long userId) {
		return userRepository.findById(userId)
				.orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND, "사용자를 찾을 수 없습니다."));
	}
}
