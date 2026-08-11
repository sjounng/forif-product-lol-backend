package com.scrim.lolscrim.domain.group;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.scrim.lolscrim.domain.group.dto.PublicRoomResponse;
import com.scrim.lolscrim.global.auth.AuthInterceptor;
import com.scrim.lolscrim.global.auth.AuthUserIdArgumentResolver;
import com.scrim.lolscrim.global.auth.JwtProvider;
import com.scrim.lolscrim.global.config.WebConfig;
import com.scrim.lolscrim.global.error.GlobalExceptionHandler;

@WebMvcTest(controllers = {
		RoomController.class,
		PublicRoomController.class,
		RoomInvitationController.class,
		GuestAdminController.class
})
@Import({
		WebConfig.class,
		AuthInterceptor.class,
		AuthUserIdArgumentResolver.class,
		GlobalExceptionHandler.class
})
class GroupApiSecurityTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private RoomService roomService;

	@MockitoBean
	private RoomInvitationService invitationService;

	@MockitoBean
	private GuestAdmissionService guestAdmissionService;

	@MockitoBean
	private JwtProvider jwtProvider;

	@Test
	void publicGroupCanBeViewedWithoutMemberAuthentication() throws Exception {
		when(roomService.getPublicRoom("ABCDEFGH"))
				.thenReturn(new PublicRoomResponse(
						1L,
						"공개 그룹",
						null,
						"ABCDEFGH",
						true,
						false,
						2));

		mockMvc.perform(get("/api/public/rooms/ABCDEFGH"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("공개 그룹"))
				.andExpect(jsonPath("$.participantCount").value(2));
	}

	@Test
	void roomManagementRequiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/rooms"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
	}
}

