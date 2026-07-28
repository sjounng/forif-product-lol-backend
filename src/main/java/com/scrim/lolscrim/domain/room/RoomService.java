package com.scrim.lolscrim.domain.room;

import java.security.SecureRandom;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.scrim.lolscrim.domain.player.PlayerRepository;
import com.scrim.lolscrim.domain.room.dto.CreateRoomRequest;
import com.scrim.lolscrim.domain.room.dto.RoomResponse;
import com.scrim.lolscrim.global.error.ApiException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RoomService {

	private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // 헷갈리는 0/O, 1/I 제외
	private static final int PUBLIC_CODE_LENGTH = 8;
	private static final SecureRandom SECURE_RANDOM = new SecureRandom();

	private final RoomRepository roomRepository;
	private final PlayerRepository playerRepository;
	private final PasswordEncoder passwordEncoder;

	@Transactional
	public RoomResponse createRoom(Long ownerUserId, CreateRoomRequest request) {
		String publicCode = generateUniquePublicCode();
		Room room = Room.create(
				ownerUserId,
				request.name(),
				request.description(),
				publicCode,
				passwordEncoder.encode(request.entryCode()),
				maskEntryCode(request.entryCode()));
		roomRepository.save(room);
		return RoomResponse.of(room, 0);
	}

	@Transactional(readOnly = true)
	public RoomResponse getRoom(Long ownerUserId, Long roomId) {
		Room room = findOwnedRoom(ownerUserId, roomId);
		return RoomResponse.of(room, playerRepository.countByRoomIdAndActiveTrue(roomId));
	}

	@Transactional(readOnly = true)
	public List<RoomResponse> listRooms(Long ownerUserId) {
		return roomRepository.findByOwnerUserIdOrderByCreatedAtDesc(ownerUserId).stream()
				.map(room -> RoomResponse.of(room, playerRepository.countByRoomIdAndActiveTrue(room.getId())))
				.toList();
	}

	public Room findOwnedRoom(Long ownerUserId, Long roomId) {
		return roomRepository.findByIdAndOwnerUserId(roomId, ownerUserId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "존재하지 않는 방입니다."));
	}

	private String generateUniquePublicCode() {
		for (int attempt = 0; attempt < 10; attempt++) {
			String code = randomCode();
			if (!roomRepository.existsByPublicCode(code)) {
				return code;
			}
		}
		throw new IllegalStateException("public_code 생성 재시도 초과");
	}

	private static String randomCode() {
		StringBuilder sb = new StringBuilder(PUBLIC_CODE_LENGTH);
		for (int i = 0; i < PUBLIC_CODE_LENGTH; i++) {
			sb.append(CODE_ALPHABET.charAt(SECURE_RANDOM.nextInt(CODE_ALPHABET.length())));
		}
		return sb.toString();
	}

	private static String maskEntryCode(String entryCode) {
		if (entryCode.length() <= 3) {
			return entryCode.charAt(0) + "*".repeat(entryCode.length() - 1);
		}
		return entryCode.charAt(0) + entryCode.charAt(1)
				+ "*".repeat(entryCode.length() - 3)
				+ entryCode.charAt(entryCode.length() - 1);
	}
}
