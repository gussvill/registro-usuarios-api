package com.registro.usuarios.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserResponseTest {

  private static final UUID ID = UUID.fromString("0b9e3b0e-6a4c-4d52-9b8e-1f1f2d6d7a10");
  private static final Instant AT = Instant.parse("2026-01-15T10:30:00Z");

  private static UserResponse response(String token) {
    return new UserResponse(
        ID,
        "Juan",
        "juan@rodriguez.org",
        List.of(new PhoneResponse("1234567", "1", "57")),
        AT,
        AT,
        AT,
        token,
        true);
  }

  @Test
  void toStringRedactsTheTokenButKeepsTheOtherFields() {
    assertThat(response("header.payload.signature").toString())
        .doesNotContain("header.payload.signature")
        .contains("token=<redacted>")
        .contains(ID.toString())
        .contains("name=Juan")
        .contains("1234567")
        .contains("active=true");
  }

  @Test
  void toStringRedactsAnyTokenIncludingNone() {
    assertThat(response("another.token.value").toString())
        .doesNotContain("another.token.value")
        .contains("token=<redacted>");
    assertThat(response(null).toString()).contains("token=<redacted>");
  }
}
