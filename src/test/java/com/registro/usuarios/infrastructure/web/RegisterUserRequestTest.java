package com.registro.usuarios.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class RegisterUserRequestTest {

  @Test
  void toStringRedactsThePasswordButKeepsTheOtherFields() {
    RegisterUserRequest request =
        new RegisterUserRequest(
            "Juan",
            "juan@rodriguez.org",
            "hunter2",
            List.of(new PhoneRequest("1234567", "1", "57")));

    assertThat(request.toString())
        .doesNotContain("hunter2")
        .contains("password=<redacted>")
        .contains("name=Juan")
        .contains("1234567");
  }

  @Test
  void toStringRedactsAnyPasswordIncludingNone() {
    assertThat(new RegisterUserRequest("Juan", "j@d.cl", "another-secret", null).toString())
        .doesNotContain("another-secret")
        .contains("password=<redacted>");
    assertThat(new RegisterUserRequest(null, null, null, null).toString())
        .contains("password=<redacted>");
  }
}
