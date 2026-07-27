package com.scrim.lolscrim.domain.user;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.scrim.lolscrim.domain.auth.dto.UserResponse;
import com.scrim.lolscrim.domain.user.dto.UpdateProfileRequest;
import com.scrim.lolscrim.global.error.ApiException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

	private final UserRepository userRepository;

	@Transactional
	public UserResponse updateProfile(Long userId, UpdateProfileRequest request) {
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "존재하지 않는 사용자입니다."));
		user.updateProfile(request.displayName(), request.avatarUrl());
		return UserResponse.from(user);
	}
}
