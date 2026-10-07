package com.registro.usuarios.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserIdTest {

  private static final String CANONICAL_UUID =
      "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$";

  @Test
  void generatesDistinctIdentifiers() {
    UserId first = UserId.generate();
    UserId second = UserId.generate();

    assertThat(first).isNotEqualTo(second);
    assertThat(first.value()).isNotEqualTo(second.value());
  }

  @Test
  void generatedIdentifierIsACanonicalRandomUuid() {
    UserId id = UserId.generate();

    assertThat(id.value().toString()).hasSize(36).matches(CANONICAL_UUID);
    assertThat(id.value().version()).isEqualTo(4);
  }

  @Test
  void identifiersWithTheSameUuidAreEqual() {
    UUID uuid = UUID.fromString("0b9e3b0e-6a4c-4d52-9b8e-1f1f2d6d7a10");

    assertThat(new UserId(uuid)).isEqualTo(new UserId(uuid)).hasSameHashCodeAs(new UserId(uuid));
    assertThat(new UserId(uuid).value()).isEqualTo(uuid);
  }

  @Test
  void rejectsAMissingUuid() {
    assertThatNullPointerException().isThrownBy(() -> new UserId(null));
  }
}
