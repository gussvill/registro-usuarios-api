package com.registro.usuarios.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Title, description and version of the generated OpenAPI document. */
@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

  @Bean
  OpenAPI userRegistrationOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("User Registration API")
                .description(
                    "Registers a user and answers with the stored data and a signed JWT. Every"
                        + " error is a JSON object with a single \"mensaje\" field.")
                .version("1.0.0"));
  }
}
