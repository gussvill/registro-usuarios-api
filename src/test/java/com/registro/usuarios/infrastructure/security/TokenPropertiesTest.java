package com.registro.usuarios.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class TokenPropertiesTest {

  @Test
  void toStringNeverPrintsTheSecretWhateverItIsEvenWhenItIsMissing() {
    TokenProperties properties =
        new TokenProperties("a-very-private-signing-secret-0123456789", Duration.ofMinutes(2));

    assertThat(properties.toString())
        .doesNotContain("a-very-private-signing-secret")
        .contains("secret=<redacted>")
        .contains("PT2M");
    assertThat(new TokenProperties(null, Duration.ofMinutes(15)).toString())
        .contains("secret=<redacted>")
        .contains("PT15M");
    assertThat(new TokenProperties("short", Duration.ofMinutes(15)).toString())
        .doesNotContain("short");
  }
}
