package com.registro.usuarios.infrastructure.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Settings of the issued tokens, bound from {@code app.token.*}.
 *
 * <p>The secret must be at least 32 bytes; the token issuer enforces that when it is created, so an
 * application with a weak secret does not start. {@link #toString()} redacts the secret so that a
 * log line or a failure report never prints it.
 *
 * @param secret key material of the HS256 signature
 * @param expiration how long a token is valid after it is issued
 */
@ConfigurationProperties(prefix = "app.token")
@Validated
public record TokenProperties(@NotBlank String secret, @NotNull Duration expiration) {

  @Override
  public String toString() {
    return "TokenProperties[secret=<redacted>, expiration=" + expiration + "]";
  }
}
