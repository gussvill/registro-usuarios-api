package com.registro.usuarios.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.registro.usuarios.domain.exception.InvalidUserDataException.Reason;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class UserNameRuleTest {

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "   ", "\t\n"})
  void aMissingOrBlankNameIsRequired(String name) {
    assertThat(User.nameViolation(name)).contains(Reason.NAME_REQUIRED);
  }

  @ParameterizedTest
  @ValueSource(strings = {"Juan Rodriguez", "J", "Ñandú Pérez", " padded name "})
  void anyOtherNameWithinTheLimitIsAccepted(String name) {
    assertThat(User.nameViolation(name)).isEmpty();
  }

  @Test
  void aNameOfExactlyTheMaximumLengthIsAccepted() {
    assertThat(User.nameViolation("n".repeat(User.NAME_MAX_LENGTH))).isEmpty();
    assertThat(User.NAME_MAX_LENGTH).isEqualTo(255);
  }

  @Test
  void aNameOneCharacterOverTheMaximumIsTooLong() {
    assertThat(User.nameViolation("n".repeat(256))).contains(Reason.NAME_TOO_LONG);
  }

  @Test
  void aTenThousandCharacterNameIsTooLong() {
    assertThat(User.nameViolation("n".repeat(10_000))).contains(Reason.NAME_TOO_LONG);
  }
}
