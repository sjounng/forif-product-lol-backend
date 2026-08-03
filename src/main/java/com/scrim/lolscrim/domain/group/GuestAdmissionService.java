package com.scrim.lolscrim.domain.group;

import com.scrim.lolscrim.domain.room.Room;
import com.scrim.lolscrim.domain.room.RoomRepository;
import com.scrim.lolscrim.domain.room.RoomStatus;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.scrim.lolscrim.domain.group.dto.GuestEntryRequest;
import com.scrim.lolscrim.domain.group.dto.GuestEntryResponse;
import com.scrim.lolscrim.domain.group.dto.GuestResponse;
import com.scrim.lolscrim.domain.group.dto.RenameGuestRequest;
import com.scrim.lolscrim.global.error.ApiException;
import com.scrim.lolscrim.global.error.ErrorCode;

import lombok.RequiredArgsConstructor;
import com.scrim.lolscrim.domain.player.PlayerRepository;

@Service
@RequiredArgsConstructor
public class GuestAdmissionService {

	private static final SecureRandom SECURE_RANDOM = new SecureRandom();

	private final RoomRepository roomRepository;
	private final GuestSessionRepository guestSessionRepository;
	private final PasswordEncoder passwordEncoder;
	private final RoomService roomService;
	private final PlayerRepository playerRepository;

	@Transactional
	public GuestAdmissionResult enter(
			String publicCode,
			GuestEntryRequest request,
			String existingToken,
			String remoteAddress) {
		Room room = requirePublicRoom(publicCode);
		if (!room.isGuestAdmissionEnabled()) {
			throw new ApiException(
					ErrorCode.GUEST_ADMISSION_DISABLED,
					"현재 신규 게스트 입장이 중지되어 있습니다.");
		}
		verifyEntryPassword(room, request.entryPassword());

		LocalDateTime now = LocalDateTime.now();
		if (existingToken != null && !existingToken.isBlank()) {
			GuestSession existing = guestSessionRepository.findByTokenHash(sha256Hex(existingToken))
					.filter(guest -> guest.getRoomId().equals(room.getId()))
					.filter(guest -> guest.isUsable(now))
					.orElse(null);
			if (existing != null) {
				existing.rename(request.nickname().trim(), now);
				return result(existingToken, room, existing, now);
			}
		}

		byte[] ip = toIpBytes(remoteAddress);
		String token = generateToken();
		GuestSession guest = guestSessionRepository.save(GuestSession.create(
				room.getId(),
				sha256Hex(token),
				request.nickname().trim(),
				ip,
				now,
				now.plusDays(30)));
		return result(token, room, guest, now);
	}

	@Transactional(readOnly = true)
	public GuestEntryResponse getCurrentGuest(String publicCode, String token) {
		Room room = requirePublicRoom(publicCode);
		if (token == null || token.isBlank()) {
			throw new ApiException(ErrorCode.AUTH_REQUIRED, "게스트 세션이 필요합니다.");
		}
		LocalDateTime now = LocalDateTime.now();
		GuestSession guest = guestSessionRepository.findByTokenHash(sha256Hex(token))
				.filter(candidate -> candidate.getRoomId().equals(room.getId()))
				.filter(candidate -> candidate.isUsable(now))
				.orElseThrow(() -> new ApiException(ErrorCode.AUTH_INVALID, "게스트 세션이 만료되었습니다."));
		return new GuestEntryResponse(
				roomService.toPublicResponse(room, now),
				GuestResponse.from(guest, now));
	}

	@Transactional(readOnly = true)
	public List<GuestResponse> getGuests(Long actorUserId, Long roomId) {
		Room room = roomService.requireRoom(roomId);
		roomService.requireManager(room, actorUserId);
		LocalDateTime now = LocalDateTime.now();
		return guestSessionRepository.findActiveByRoomId(roomId, now)
				.stream()
				.map(guest -> GuestResponse.from(guest, now))
				.toList();
	}

	@Transactional
	public GuestResponse renameGuest(
			Long actorUserId,
			Long roomId,
			Long guestId,
			RenameGuestRequest request) {
		Room room = roomService.requireRoom(roomId);
		roomService.requireManager(room, actorUserId);
		GuestSession guest = requireGuest(roomId, guestId);
		LocalDateTime now = LocalDateTime.now();
		guest.rename(request.nickname().trim(), now);
		return GuestResponse.from(guest, now);
	}

	@Transactional
	public void removeGuest(Long actorUserId, Long roomId, Long guestId) {
		Room room = roomService.requireRoom(roomId);
		roomService.requireManager(room, actorUserId);
		GuestSession guest = requireGuest(roomId, guestId);
		LocalDateTime now = LocalDateTime.now();
		guest.eject(now);
		playerRepository.findByRoomIdAndGuestSessionId(roomId, guestId)
				.ifPresent(player -> player.deactivate(now));
	}

	@Transactional
	public void leave(String publicCode, String token) {
		Room room = requirePublicRoom(publicCode);
		if (token == null || token.isBlank()) {
			return;
		}
		GuestSession guest = guestSessionRepository.findByTokenHash(sha256Hex(token))
				.filter(candidate -> candidate.getRoomId().equals(room.getId()))
				.orElse(null);
		if (guest == null) {
			return;
		}
		LocalDateTime now = LocalDateTime.now();
		guest.eject(now);
		playerRepository.findByRoomIdAndGuestSessionId(room.getId(), guest.getId())
				.ifPresent(player -> player.deactivate(now));
	}

	private GuestAdmissionResult result(
			String token,
			Room room,
			GuestSession guest,
			LocalDateTime now) {
		return new GuestAdmissionResult(
				token,
				new GuestEntryResponse(
						roomService.toPublicResponse(room, now),
						GuestResponse.from(guest, now)));
	}

	private Room requirePublicRoom(String publicCode) {
		return roomRepository.findByPublicCodeAndStatus(publicCode, RoomStatus.ACTIVE)
				.orElseThrow(() -> new ApiException(ErrorCode.ROOM_NOT_FOUND, "그룹을 찾을 수 없습니다."));
	}

	private GuestSession requireGuest(Long roomId, Long guestId) {
		return guestSessionRepository.findById(guestId)
				.filter(guest -> guest.getRoomId().equals(roomId))
				.orElseThrow(() -> new ApiException(ErrorCode.GUEST_NOT_FOUND, "게스트를 찾을 수 없습니다."));
	}

	private void verifyEntryPassword(Room room, String password) {
		if (room.getEntryCodeHash() == null) {
			return;
		}
		if (password == null
				|| password.isBlank()
				|| !passwordEncoder.matches(password, room.getEntryCodeHash())) {
			throw new ApiException(
					ErrorCode.GUEST_ENTRY_PASSWORD_INVALID,
					"입장 암호가 올바르지 않습니다.");
		}
	}

	private static String generateToken() {
		byte[] bytes = new byte[32];
		SECURE_RANDOM.nextBytes(bytes);
		return HexFormat.of().formatHex(bytes);
	}

	private static String sha256Hex(String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is not available", e);
		}
	}

	private static byte[] toIpBytes(String remoteAddress) {
		if (remoteAddress == null || remoteAddress.isBlank()) {
			return null;
		}
		try {
			return InetAddress.getByName(remoteAddress).getAddress();
		} catch (UnknownHostException e) {
			return null;
		}
	}

	public record GuestAdmissionResult(String token, GuestEntryResponse response) {
	}
}
