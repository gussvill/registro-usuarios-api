package com.registro.usuarios.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RegexPasswordPolicyTest {

  /** Same shape as the production default: a letter, a digit, 7 to 72 non-space characters. */
  private static final Pattern DEFAULT = Pattern.compile("^(?=.*[A-Za-z])(?=.*[0-9])\\S{7,72}$");

  @ParameterizedTest
  @ValueSource(strings = {"hunter2", "abc12345", "A1bcdefg"})
  void theDefaultPatternAcceptsPasswordsWithALetterAndADigit(String password) {
    assertThat(new RegexPasswordPolicy(DEFAULT).isSatisfiedBy(password)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"abc12", "abcdefgh", "12345678", "hunter 2", "hunter2 ", "short1"})
  void theDefaultPatternRejectsShortSpacedOrSingleKindPasswords(String password) {
    assertThat(new RegexPasswordPolicy(DEFAULT).isSatisfiedBy(password)).isFalse();
  }

  @Test
  void theDefaultPatternAcceptsSeventyTwoCharactersAndRejectsSeventyThree() {
    RegexPasswordPolicy policy = new RegexPasswordPolicy(DEFAULT);

    assertThat(policy.isSatisfiedBy("x".repeat(71) + "1")).isTrue();
    assertThat(policy.isSatisfiedBy("x".repeat(72) + "1")).isFalse();
  }

  @Test
  void aPermissivePatternAcceptsAnything() {
    RegexPasswordPolicy policy = new RegexPasswordPolicy(Pattern.compile("^.+$"));

    assertThat(policy.isSatisfiedBy("a")).isTrue();
    assertThat(policy.isSatisfiedBy("abc12")).isTrue();
  }

  @Test
  void theRuleFollowsTheConfiguredPattern() {
    RegexPasswordPolicy upperCaseOnly = new RegexPasswordPolicy(Pattern.compile("^[A-Z]{8,}$"));

    assertThat(upperCaseOnly.isSatisfiedBy("hunter2")).isFalse();
    assertThat(upperCaseOnly.isSatisfiedBy("ABCDEFGH")).isTrue();
  }

  @Test
  void theWholeValueMustMatchSoATrailingNewlineIsNotIgnored() {
    assertThat(new RegexPasswordPolicy(DEFAULT).isSatisfiedBy("hunter2\n")).isFalse();
  }

  @Test
  void aMissingPasswordNeverSatisfiesThePolicy() {
    assertThat(new RegexPasswordPolicy(Pattern.compile("^.*$")).isSatisfiedBy(null)).isFalse();
  }

  @Test
  void aMissingPatternIsAProgrammingError() {
    assertThatNullPointerException().isThrownBy(() -> new RegexPasswordPolicy(null));
  }
}
