package com.registro.usuarios.infrastructure.security;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuración de los tokens emitidos, enlazada desde {@code app.token.*}.
 *
 * <p>El secreto es opcional. Cuando está ausente o vacío, el emisor de tokens genera una clave
 * efímera al arrancar; cuando se define debe tener al menos 32 bytes, lo cual el emisor de tokens
 * comprueba al crearse, de modo que una aplicación con un secreto débil no arranca. {@link
 * #toString()} oculta el secreto para que una línea de log o un informe de fallo nunca lo imprima.
 *
 * @param secret material de la clave de la firma HS256, o nulo o vacío para usar una clave efímera
 * @param expiration cuánto tiempo es válido un token después de emitirse; positivo y de 24 horas
 *     como máximo, lo cual comprueba el emisor de tokens al crearse
 */
@ConfigurationProperties(prefix = "app.token")
@Validated
public record TokenProperties(String secret, @NotNull Duration expiration) {

  @Override
  public String toString() {
    return "TokenProperties[secret=<redacted>, expiration=" + expiration + "]";
  }
}
