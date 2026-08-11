package com.scrim.lolscrim.domain.session;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

@WebMvcTest(controllers = SessionController.class)
@Import({
		WebConfig.class,
		AuthInterceptor.class,
		AuthUserIdArgumentResolver.class,
		GlobalExceptionHandler.class
})
class SessionApiSecurityTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private SessionService sessionService;

	@MockitoBean
	private JwtProvider jwtProvider;

	@Test
	void sessionListRequiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/rooms/7/sessions"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
	}
}
