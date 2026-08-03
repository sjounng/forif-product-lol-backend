package com.scrim.lolscrim.domain.group;

import java.time.Duration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.scrim.lolscrim.domain.group.GuestAdmissionService.GuestAdmissionResult;
import com.scrim.lolscrim.domain.group.dto.GuestEntryRequest;
import com.scrim.lolscrim.domain.group.dto.GuestEntryResponse;
import com.scrim.lolscrim.domain.group.dto.PublicRoomResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/public/rooms")
@RequiredArgsConstructor
public class PublicRoomController {

	public static final String GUEST_COOKIE = "scrim_guest_session";

	private final RoomService roomService;
	private final GuestAdmissionService guestAdmissionService;

	@GetMapping("/{publicCode}")
	public PublicRoomResponse getPublicRoom(@PathVariable String publicCode) {
		return roomService.getPublicRoom(publicCode);
	}

	@PostMapping("/{publicCode}/guests")
	@ResponseStatus(HttpStatus.CREATED)
	public GuestEntryResponse enter(
			@PathVariable String publicCode,
			@Valid @RequestBody GuestEntryRequest request,
			@CookieValue(name = GUEST_COOKIE, required = false) String existingToken,
			HttpServletRequest httpRequest,
			HttpServletResponse httpResponse) {
		GuestAdmissionResult result = guestAdmissionService.enter(
				publicCode,
				request,
				existingToken,
				httpRequest.getRemoteAddr());
		httpResponse.addHeader(
				HttpHeaders.SET_COOKIE,
				guestCookie(result.token(), httpRequest.isSecure()).toString());
		return result.response();
	}

	@GetMapping("/{publicCode}/guests/me")
	public GuestEntryResponse me(
			@PathVariable String publicCode,
			@CookieValue(name = GUEST_COOKIE, required = false) String token) {
		return guestAdmissionService.getCurrentGuest(publicCode, token);
	}

	@DeleteMapping("/{publicCode}/guests/me")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void leave(
			@PathVariable String publicCode,
			@CookieValue(name = GUEST_COOKIE, required = false) String token,
			HttpServletRequest httpRequest,
			HttpServletResponse httpResponse) {
		guestAdmissionService.leave(publicCode, token);
		httpResponse.addHeader(
				HttpHeaders.SET_COOKIE,
				ResponseCookie.from(GUEST_COOKIE, "")
						.httpOnly(true)
						.secure(httpRequest.isSecure())
						.sameSite("Lax")
						.path("/")
						.maxAge(Duration.ZERO)
						.build()
						.toString());
	}

	private static ResponseCookie guestCookie(String token, boolean secure) {
		return ResponseCookie.from(GUEST_COOKIE, token)
				.httpOnly(true)
				.secure(secure)
				.sameSite("Lax")
				.path("/")
				.maxAge(Duration.ofDays(30))
				.build();
	}
}
