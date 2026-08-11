package com.scrim.lolscrim.domain.player;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.scrim.lolscrim.global.auth.AuthInterceptor;
import com.scrim.lolscrim.global.auth.AuthUserIdArgumentResolver;
import com.scrim.lolscrim.global.auth.JwtProvider;
import com.scrim.lolscrim.global.config.WebConfig;
import com.scrim.lolscrim.global.error.GlobalExceptionHandler;

@WebMvcTest(controllers = RiotPlayerController.class)
@Import({
		WebConfig.class,
		AuthInterceptor.class,
		AuthUserIdArgumentResolver.class,
		GlobalExceptionHandler.class
})
class RiotPlayerApiSecurityTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private RiotPlayerService riotPlayerService;

	@MockitoBean
	private JwtProvider jwtProvider;

	@Test
	void playerListRequiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/rooms/7/players"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
	}

	@Test
	void playerRegistrationRequiresAuthentication() throws Exception {
		mockMvc.perform(post("/api/rooms/7/players")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"gameName":"Hide on bush","tagLine":"KR1"}
							"""))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
	}
}
