package com.registro.usuarios.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.registro.usuarios.domain.model.Reason;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The bounds that hold whatever the configured format: required, and at most {@link
 * Password#MAX_BYTES} UTF-8 bytes.
 */
class PasswordTest {

  private static final PasswordPolicy ANYTHING = new RegexPasswordPolicy(Pattern.compile("^.+$"));

  /** Counts how many times the format rule is consulted. */
  private static final class CountingPolicy implements PasswordPolicy {
    private final AtomicInteger calls = new AtomicInteger();
    private final boolean verdict;

    CountingPolicy(boolean verdict) {
      this.verdict = verdict;
    }

    @Override
    public boolean isSatisfiedBy(String rawPassword) {
      calls.incrementAndGet();
      return verdict;
    }
  }

  @Test
  void aPolicyOnlyDecidesTheFormatSoTheFixedLimitsCannotBeOverridden() {
    assertThat(PasswordPolicy.class.getDeclaredMethods())
        .extracting(Method::getName)
        .containsExactly("isSatisfiedBy");
    assertThat(Modifier.isFinal(Password.class.getModifiers())).isTrue();
  }

  @Test
  void theByteLimitIsSeventyTwo() {
    assertThat(Password.MAX_BYTES).isEqualTo(72);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "       ", "\t\n"})
  void aMissingOrBlankPasswordIsRequiredAndTheFormatIsNotConsulted(String password) {
    CountingPolicy format = new CountingPolicy(false);

    assertThat(Password.violation(password, format)).contains(Reason.PASSWORD_REQUIRED);
    assertThat(format.calls).hasValue(0);
  }

  @Test
  void seventyTwoAsciiCharactersAreAcceptedWhateverThePattern() {
    assertThat(Password.violation("a".repeat(72), ANYTHING)).isEmpty();
  }

  @Test
  void seventyThreeAsciiCharactersAreTooLongEvenWithAPermissivePattern() {
    assertThat(Password.violation("a".repeat(73), ANYTHING)).contains(Reason.PASSWORD_TOO_LONG);
  }

  @Test
  void fortyAccentedCharactersAreEightyBytesAndTooLong() {
    String password = "é".repeat(40);

    assertThat(password).hasSize(40);
    assertThat(Password.violation(password, ANYTHING)).contains(Reason.PASSWORD_TOO_LONG);
  }

  @Test
  void thirtySixAccentedCharactersAreExactlySeventyTwoBytesAndAccepted() {
    assertThat(Password.violation("é".repeat(36), ANYTHING)).isEmpty();
  }

  @Test
  void supplementaryCharactersAreCountedInBytesNotInUtf16Units() {
    // U+1F511 is two UTF-16 units and four UTF-8 bytes: 19 of them are 38 units but 76 bytes.
    String keys = "🔑".repeat(19);

    assertThat(keys).hasSize(38);
    assertThat(Password.violation(keys, ANYTHING)).contains(Reason.PASSWORD_TOO_LONG);
  }

  @Test
  @Timeout(value = 5)
  void aHundredThousandCharacterPasswordIsRejectedBeforeTheFormatRuleRuns() {
    CountingPolicy format = new CountingPolicy(true);

    assertThat(Password.violation("a".repeat(100_000), format)).contains(Reason.PASSWORD_TOO_LONG);
    assertThat(format.calls).hasValue(0);
  }

  @Test
  void lengthIsCheckedBeforeFormat() {
    CountingPolicy rejectingFormat = new CountingPolicy(false);

    assertThat(Password.violation("a".repeat(73), rejectingFormat))
        .contains(Reason.PASSWORD_TOO_LONG);
    assertThat(rejectingFormat.calls).hasValue(0);
  }

  @Test
  void aPasswordWithinTheBoundsIsJudgedByTheFormatRule() {
    CountingPolicy accepting = new CountingPolicy(true);
    CountingPolicy rejecting = new CountingPolicy(false);

    assertThat(Password.violation("hunter2", accepting)).isEmpty();
    assertThat(Password.violation("hunter2", rejecting)).contains(Reason.PASSWORD_FORMAT);
    assertThat(accepting.calls).hasValue(1);
    assertThat(rejecting.calls).hasValue(1);
  }
}
