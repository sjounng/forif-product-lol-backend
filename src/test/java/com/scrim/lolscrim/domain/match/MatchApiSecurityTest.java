package com.scrim.lolscrim.domain.match;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.scrim.lolscrim.global.auth.AuthInterceptor;
import com.scrim.lolscrim.global.auth.AuthUserIdArgumentResolver;
import com.scrim.lolscrim.global.auth.JwtProvider;
import com.scrim.lolscrim.global.config.WebConfig;
import com.scrim.lolscrim.global.error.GlobalExceptionHandler;

@WebMvcTest(controllers = MatchController.class)
@Import({
		WebConfig.class,
		AuthInterceptor.class,
		AuthUserIdArgumentResolver.class,
		GlobalExceptionHandler.class
})
class MatchApiSecurityTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private MatchService matchService;

	@MockitoBean
	private JwtProvider jwtProvider;

	@Test
	void matchOverviewRequiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/sessions/7/matches"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
	}

	@Test
	void matchStartRequestRequiresAuthentication() throws Exception {
		mockMvc.perform(post("/api/sessions/7/match-start-requests"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
	}
}
