package com.registro.usuarios.domain.model;

import static com.registro.usuarios.support.Rejections.reasonsOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.registro.usuarios.domain.exception.Reason;
import java.util.Locale;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class EmailTest {

  /**
   * Misma forma que el valor por defecto de producción; el patrón es un parámetro de la regla de
   * dominio.
   */
  private static final Pattern FORMAT =
      Pattern.compile("^[a-z0-9._%+-]+@[a-z0-9-]+(\\.[a-z0-9-]+)*\\.[a-z]{2,}$");

  private static final Pattern ANYTHING = Pattern.compile("^.+$");

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", " ", "   ", "\t\n"})
  void missingOrBlankValueIsRequiredAndNothingElse(String raw) {
    assertThat(Email.violation(raw, FORMAT)).contains(Reason.EMAIL_REQUIRED);
    assertThat(reasonsOf(() -> Email.of(raw, FORMAT))).containsExactly(Reason.EMAIL_REQUIRED);
  }

  @ParameterizedTest
  @ValueSource(strings = {"aaaaaaa@dominio.cl", "juan@rodriguez.org", "a.b+c@sub.dominio.co.uk"})
  void acceptsTheValidSamples(String raw) {
    assertThat(Email.violation(raw, FORMAT)).isEmpty();
    assertThat(Email.of(raw, FORMAT).value()).isEqualTo(raw);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "juan",
        "juan@",
        "@dominio.cl",
        "juan@dominio",
        "juan@@dominio.cl",
        " juan@dominio.cl",
        "juan@dominio.cl ",
        "juan@rodriguez.org\n"
      })
  void rejectsTheInvalidSamplesWithTheFormatReason(String raw) {
    assertThat(Email.violation(raw, FORMAT)).contains(Reason.EMAIL_FORMAT);
    assertThat(reasonsOf(() -> Email.of(raw, FORMAT))).containsExactly(Reason.EMAIL_FORMAT);
  }

  @Test
  void doesNotTrimSoLeadingSpaceReachesThePatternAndFails() {
    assertThat(Email.violation(" juan@dominio.cl", FORMAT)).contains(Reason.EMAIL_FORMAT);
    assertThat(Email.violation(" juan@dominio.cl", ANYTHING)).isEmpty();
    assertThat(Email.of(" juan@dominio.cl", ANYTHING).value()).isEqualTo(" juan@dominio.cl");
  }

  @Test
  void aTrailingNewlineIsRejectedBecauseThePatternMustConsumeTheWholeInput() {
    Pattern endsWithDollar = Pattern.compile("^[a-z]+@dominio\\.cl$");

    assertThat(Email.violation("juan@dominio.cl", endsWithDollar)).isEmpty();
    assertThat(Email.violation("juan@dominio.cl\n", endsWithDollar)).contains(Reason.EMAIL_FORMAT);
  }

  @Test
  void acceptsAnEmailOfExactlyTheMaximumLength() {
    String raw = "a".repeat(Email.MAX_LENGTH - "@dominio.cl".length()) + "@dominio.cl";

    assertThat(raw).hasSize(Email.MAX_LENGTH).hasSize(254);
    assertThat(Email.violation(raw, FORMAT)).isEmpty();
    assertThat(Email.of(raw, FORMAT).value()).hasSize(254);
  }

  @Test
  void rejectsOneCharacterOverTheMaximumWithTheLengthReason() {
    String raw = "a".repeat(Email.MAX_LENGTH + 1 - "@dominio.cl".length()) + "@dominio.cl";

    assertThat(raw).hasSize(255);
    assertThat(Email.violation(raw, FORMAT)).contains(Reason.EMAIL_TOO_LONG);
    assertThat(reasonsOf(() -> Email.of(raw, FORMAT))).containsExactly(Reason.EMAIL_TOO_LONG);
  }

  @Test
  void lengthIsCheckedBeforeFormatSoAnOversizedValueIsNeverMatched() {
    String oversizedAndMalformed = "a".repeat(255);

    assertThat(Email.violation(oversizedAndMalformed, FORMAT)).contains(Reason.EMAIL_TOO_LONG);
  }

  @Test
  @Timeout(value = 5)
  void aFiftyThousandCharacterEmailIsRejectedByLengthWithoutRunningTheExpensivePattern() {
    Pattern catastrophic = Pattern.compile("^(a+)+$");
    String hostile = "a".repeat(50_000) + "@x";

    assertThat(Email.violation(hostile, catastrophic)).contains(Reason.EMAIL_TOO_LONG);
  }

  @Test
  void requiredIsCheckedBeforeLength() {
    assertThat(Email.violation("   ", FORMAT)).contains(Reason.EMAIL_REQUIRED);
  }

  @Test
  void lowerCasesTheValue() {
    assertThat(Email.of("Juan@Rodriguez.ORG", FORMAT).value()).isEqualTo("juan@rodriguez.org");
  }

  @Test
  void lowerCasingIsLocaleIndependent() {
    Locale previous = Locale.getDefault();
    try {
      Locale.setDefault(Locale.forLanguageTag("tr-TR"));

      assertThat("INFO@DOMINIO.CL".toLowerCase(Locale.getDefault()))
          .isNotEqualTo("info@dominio.cl");
      assertThat(Email.of("INFO@DOMINIO.CL", FORMAT).value()).isEqualTo("info@dominio.cl");
    } finally {
      Locale.setDefault(previous);
    }
  }

  @Test
  void lengthIsMeasuredOnTheLowerCasedValue() {
    // U+0130 se pasa a minúsculas en dos caracteres ('i' + punto combinante) con Locale.ROOT.
    String raw = "İ".repeat(Email.MAX_LENGTH / 2 + 1);

    assertThat(raw).hasSizeLessThan(Email.MAX_LENGTH);
    assertThat(raw.toLowerCase(Locale.ROOT)).hasSizeGreaterThan(Email.MAX_LENGTH);
    assertThat(Email.violation(raw, ANYTHING)).contains(Reason.EMAIL_TOO_LONG);
  }

  @Test
  void emailsDifferingOnlyInCaseAreEqual() {
    Email lower = Email.of("juan@rodriguez.org", FORMAT);
    Email mixed = Email.of("JUAN@Rodriguez.ORG", FORMAT);

    assertThat(mixed).isEqualTo(lower).hasSameHashCodeAs(lower);
  }

  @Test
  void differentEmailsAreNotEqual() {
    assertThat(Email.of("juan@rodriguez.org", FORMAT))
        .isNotEqualTo(Email.of("ana@rodriguez.org", FORMAT))
        .isNotEqualTo("juan@rodriguez.org");
  }

  @Test
  void maskedKeepsTheDomainAndHidesTheLocalPart() {
    Email email = Email.of("Juan@Rodriguez.ORG", FORMAT);

    assertThat(email.masked()).isEqualTo("***@rodriguez.org");
    assertThat(email.masked()).doesNotContain("juan");
  }

  @Test
  void maskedCopesWithAValueWithoutAtSignAllowedByACustomPattern() {
    assertThat(Email.of("nodomain", ANYTHING).masked()).isEqualTo("***");
  }

  @Test
  void toStringNeverPrintsTheFullAddress() {
    Email email = Email.of("juan@rodriguez.org", FORMAT);

    assertThat(email.toString()).isEqualTo("***@rodriguez.org").doesNotContain("juan@");
  }

  @Test
  void aMissingFormatIsAProgrammingError() {
    assertThatNullPointerException().isThrownBy(() -> Email.violation("juan@rodriguez.org", null));
  }

  @Test
  @Timeout(value = 5)
  void aHundredThousandCharacterBlankIsStillRequiredAndFast() {
    assertThat(Email.violation(" ".repeat(100_000), FORMAT)).contains(Reason.EMAIL_REQUIRED);
  }
}
