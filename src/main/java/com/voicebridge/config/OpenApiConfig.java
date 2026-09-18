package com.voicebridge.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

  private static final String JWT_SCHEME = "bearerAuth";

  @Bean
  public OpenAPI openAPI() {
    return new OpenAPI()
        .info(new Info().title("42VoiceBridge API").version("v1"))
        .addSecurityItem(new SecurityRequirement().addList(JWT_SCHEME))
        .components(
            new Components()
                .addSecuritySchemes(
                    JWT_SCHEME,
                    new SecurityScheme()
                        .name(JWT_SCHEME)
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")));
  }
}
