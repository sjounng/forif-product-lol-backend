package com.scrim.lolscrim.domain.champion;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.scrim.lolscrim.domain.auth.AuthController;
import com.scrim.lolscrim.domain.auth.AuthService;
import com.scrim.lolscrim.domain.champion.dto.ChampionResponse;
import com.scrim.lolscrim.global.auth.AuthInterceptor;
import com.scrim.lolscrim.global.auth.AuthUserIdArgumentResolver;
import com.scrim.lolscrim.global.auth.JwtProvider;
import com.scrim.lolscrim.global.config.WebConfig;
import com.scrim.lolscrim.global.error.GlobalExceptionHandler;

@WebMvcTest(controllers = { ChampionController.class, AuthController.class })
@Import({
		WebConfig.class,
		AuthInterceptor.class,
		AuthUserIdArgumentResolver.class,
		GlobalExceptionHandler.class
})
class ChampionApiSecurityTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ChampionService championService;

	@MockitoBean
	private AuthService authService;

	@MockitoBean
	private JwtProvider jwtProvider;

	@Test
	void championsAreAvailableWithoutAuthentication() throws Exception {
		when(championService.getActiveChampions()).thenReturn(List.of(
				new ChampionResponse(
						266,
						"Aatrox",
						"아트록스",
						"Aatrox",
						List.of("Fighter"),
						"https://ddragon.example/Aatrox.png",
						"16.14.1")));

		mockMvc.perform(get("/api/champions"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value(266))
				.andExpect(jsonPath("$[0].riotId").value("Aatrox"))
				.andExpect(jsonPath("$[0].nameKo").value("아트록스"));
	}

	@Test
	void championsAllowRequestsFromLocalFrontend() throws Exception {
		mockMvc.perform(options("/api/champions")
						.header("Origin", "http://localhost:3000")
						.header("Access-Control-Request-Method", "GET"))
				.andExpect(status().isOk())
				.andExpect(header().string(
						"Access-Control-Allow-Origin",
						"http://localhost:3000"));
	}

	@Test
	void otherProtectedApiStillRequiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/auth/me"))
				.andExpect(status().isUnauthorized());
	}
}
