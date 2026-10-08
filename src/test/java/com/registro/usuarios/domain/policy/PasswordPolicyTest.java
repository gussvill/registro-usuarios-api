package com.registro.usuarios.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.registro.usuarios.domain.model.Reason;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The bounds that hold whatever the configured format: required, and at most {@link
 * PasswordPolicy#MAX_BYTES} UTF-8 bytes.
 */
class PasswordPolicyTest {

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
  void theByteLimitIsSeventyTwo() {
    assertThat(PasswordPolicy.MAX_BYTES).isEqualTo(72);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "       ", "\t\n"})
  void aMissingOrBlankPasswordIsRequiredAndTheFormatIsNotConsulted(String password) {
    CountingPolicy format = new CountingPolicy(false);

    assertThat(format.violation(password)).contains(Reason.PASSWORD_REQUIRED);
    assertThat(format.calls).hasValue(0);
  }

  @Test
  void seventyTwoAsciiCharactersAreAcceptedWhateverThePattern() {
    assertThat(ANYTHING.violation("a".repeat(72))).isEmpty();
  }

  @Test
  void seventyThreeAsciiCharactersAreTooLongEvenWithAPermissivePattern() {
    assertThat(ANYTHING.violation("a".repeat(73))).contains(Reason.PASSWORD_TOO_LONG);
  }

  @Test
  void fortyAccentedCharactersAreEightyBytesAndTooLong() {
    String password = "é".repeat(40);

    assertThat(password).hasSize(40);
    assertThat(ANYTHING.violation(password)).contains(Reason.PASSWORD_TOO_LONG);
  }

  @Test
  void thirtySixAccentedCharactersAreExactlySeventyTwoBytesAndAccepted() {
    assertThat(ANYTHING.violation("é".repeat(36))).isEmpty();
  }

  @Test
  void supplementaryCharactersAreCountedInBytesNotInUtf16Units() {
    // U+1F511 is two UTF-16 units and four UTF-8 bytes: 19 of them are 38 units but 76 bytes.
    String keys = "🔑".repeat(19);

    assertThat(keys).hasSize(38);
    assertThat(ANYTHING.violation(keys)).contains(Reason.PASSWORD_TOO_LONG);
  }

  @Test
  @Timeout(value = 5)
  void aHundredThousandCharacterPasswordIsRejectedBeforeTheFormatRuleRuns() {
    CountingPolicy format = new CountingPolicy(true);

    assertThat(format.violation("a".repeat(100_000))).contains(Reason.PASSWORD_TOO_LONG);
    assertThat(format.calls).hasValue(0);
  }

  @Test
  void lengthIsCheckedBeforeFormat() {
    CountingPolicy rejectingFormat = new CountingPolicy(false);

    assertThat(rejectingFormat.violation("a".repeat(73))).contains(Reason.PASSWORD_TOO_LONG);
    assertThat(rejectingFormat.calls).hasValue(0);
  }

  @Test
  void aPasswordWithinTheBoundsIsJudgedByTheFormatRule() {
    CountingPolicy accepting = new CountingPolicy(true);
    CountingPolicy rejecting = new CountingPolicy(false);

    assertThat(accepting.violation("hunter2")).isEmpty();
    assertThat(rejecting.violation("hunter2")).contains(Reason.PASSWORD_FORMAT);
    assertThat(accepting.calls).hasValue(1);
    assertThat(rejecting.calls).hasValue(1);
  }
}
