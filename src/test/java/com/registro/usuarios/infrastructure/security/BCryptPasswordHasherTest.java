package com.registro.usuarios.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class BCryptPasswordHasherTest {

  private static final Pattern BCRYPT_SHAPE = Pattern.compile("^\\$2[aby]\\$\\d{2}\\$.{53}$");

  /** La fuerza 4 es el mínimo que acepta BCrypt: el algoritmo es el mismo, solo cambia el costo. */
  private final BCryptPasswordHasher hasher = new BCryptPasswordHasher(4);

  private final BCryptPasswordEncoder verifier = new BCryptPasswordEncoder();

  @Test
  void theHashHasTheBcryptShapeAndIsNotThePassword() {
    String hash = hasher.hash("hunter2");

    assertThat(hash).matches(BCRYPT_SHAPE).isNotEqualTo("hunter2").doesNotContain("hunter2");
    assertThat(hash).hasSize(60);
  }

  @Test
  void theHashVerifiesAgainstThePasswordAndOnlyAgainstThatPassword() {
    String hash = hasher.hash("hunter2");

    assertThat(verifier.matches("hunter2", hash)).isTrue();
    assertThat(verifier.matches("hunter3", hash)).isFalse();
  }

  @Test
  void theSamePasswordHashedTwiceGivesDifferentHashesBecauseOfTheSalt() {
    String first = hasher.hash("hunter2");
    String second = hasher.hash("hunter2");

    assertThat(first).isNotEqualTo(second);
    assertThat(verifier.matches("hunter2", first)).isTrue();
    assertThat(verifier.matches("hunter2", second)).isTrue();
  }

  @Test
  void aPasswordOfExactly72BytesIsHashedAndVerified() {
    String password = "é".repeat(36);
    assertThat(password.getBytes(java.nio.charset.StandardCharsets.UTF_8)).hasSize(72);

    String hash = hasher.hash(password);

    assertThat(verifier.matches(password, hash)).isTrue();
  }

  @Test
  void theStrengthIsTheCostFactorOfTheHash() {
    assertThat(new BCryptPasswordHasher(4).hash("hunter2")).startsWith("$2a$04$");
    assertThat(new BCryptPasswordHasher(5).hash("hunter2")).startsWith("$2a$05$");
  }

  @Test
  void productionStrengthIs12() {
    assertThat(new BCryptPasswordHasher().hash("hunter2")).startsWith("$2a$12$");
  }
}
