package com.registro.usuarios.infrastructure.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Reglas de registro que son configuración del operador, enlazadas desde {@code
 * app.registration.*}. Ambas son expresiones regulares: el patrón del correo se aplica a la
 * dirección en minúsculas y el de la contraseña a la contraseña tal como se recibió. Las longitudes
 * se comprueban antes de ejecutar cualquiera de los patrones.
 *
 * @param emailPattern formato de una dirección de correo
 * @param passwordPattern formato de una contraseña
 */
@ConfigurationProperties(prefix = "app.registration")
@Validated
public record RegistrationProperties(
    @NotBlank String emailPattern, @NotBlank String passwordPattern) {}
