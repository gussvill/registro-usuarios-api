package com.registro.usuarios.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Título, descripción y versión del documento OpenAPI generado, escritos en español. */
@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

  @Bean
  OpenAPI registroUsuariosOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("API de registro de usuarios")
                .description(
                    "Registra un usuario y responde con los datos almacenados y un JWT firmado."
                        + " Todo error es un objeto JSON con un único campo \"mensaje\".")
                .version("1.0.0"));
  }
}
