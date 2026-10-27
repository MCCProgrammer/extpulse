package com.mccprogrammer.extpulse;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The API description springdoc builds from the controllers, served at /v3/api-docs and
 * shown at /swagger-ui.html. "Authorize" there takes the token from POST /api/auth/login.
 */
@Configuration(proxyBeanMethods = false)
class OpenApiConfiguration {

	private static final String BEARER = "bearer";

	@Bean
	OpenAPI openApi() {
		return new OpenAPI()
			.info(new Info().title("ExtPulse API")
				.version("v1")
				.description("Daily stats for VS Code and Chrome extensions across the VS Code Marketplace, "
						+ "Open VSX and the Chrome Web Store. Log in with POST /api/auth/login and use the token "
						+ "as a Bearer token. Errors are Problem Details (RFC 9457)."))
			.components(new Components().addSecuritySchemes(BEARER,
					new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
			.addSecurityItem(new SecurityRequirement().addList(BEARER));
	}

}
