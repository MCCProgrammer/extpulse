package com.mccprogrammer.extpulse;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiTests {

	@Autowired
	private MockMvc mvc;

	@Test
	void describesTheApiWithoutALogin() throws Exception {
		this.mvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.info.title").value("ExtPulse API"))
			.andExpect(jsonPath("$.paths['/api/publishers'].post").exists())
			.andExpect(jsonPath("$.paths['/api/extensions/{id}/stats'].get").exists())
			.andExpect(jsonPath("$.components.securitySchemes.bearer.scheme").value("bearer"));
	}

	@Test
	void servesSwaggerUi() throws Exception {
		this.mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
	}

}
