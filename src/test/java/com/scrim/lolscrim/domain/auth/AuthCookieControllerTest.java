package com.scrim.lolscrim.domain.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.scrim.lolscrim.domain.auth.dto.AuthResponse;
import com.scrim.lolscrim.domain.auth.dto.LoginRequest;
import com.scrim.lolscrim.domain.auth.dto.UserResponse;
import com.scrim.lolscrim.global.auth.AuthInterceptor;
import com.scrim.lolscrim.global.auth.AuthUserIdArgumentResolver;
import com.scrim.lolscrim.global.auth.JwtProvider;
import com.scrim.lolscrim.global.config.WebConfig;
import com.scrim.lolscrim.global.error.GlobalExceptionHandler;

@WebMvcTest(controllers = AuthController.class)
@Import({
		WebConfig.class,
		AuthInterceptor.class,
		AuthUserIdArgumentResolver.class,
		GlobalExceptionHandler.class
})
class AuthCookieControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AuthService authService;

	@MockitoBean
	private JwtProvider jwtProvider;

	@Test
	void loginStoresRefreshTokenOnlyInHttpOnlyCookie() throws Exception {
		when(authService.login(any(LoginRequest.class), any(), any()))
				.thenReturn(AuthResponse.of(
						"access-token",
						1800,
						"refresh-token",
						new UserResponse(1L, "user@example.com", "사용자", null)));

		mockMvc.perform(post("/api/auth/login")
						.contentType("application/json")
						.content("""
								{
								  "email": "user@example.com",
								  "password": "password123"
								}
								"""))
				.andExpect(status().isOk())
				.andExpect(header().string(
						"Set-Cookie",
						org.hamcrest.Matchers.allOf(
								org.hamcrest.Matchers.containsString("scrim_refresh_token=refresh-token"),
								org.hamcrest.Matchers.containsString("HttpOnly"))))
				.andExpect(jsonPath("$.accessToken").value("access-token"))
				.andExpect(jsonPath("$.refreshToken").doesNotExist());
	}
}

