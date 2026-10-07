package com.registro.usuarios.domain.model;

import static com.registro.usuarios.support.Rejections.reasonsOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.registro.usuarios.domain.exception.InvalidUserDataException.Reason;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class PhoneTest {

  private static final String NUMBER = "1234567";
  private static final String CITY = "1";
  private static final String COUNTRY = "57";

  @Test
  void aCompletePhoneHasNoViolationsAndKeepsTheValuesAsReceived() {
    assertThat(Phone.violations(NUMBER, CITY, COUNTRY)).isEmpty();

    Phone phone = new Phone("0012-345", "01", "+57");

    assertThat(phone.number()).isEqualTo("0012-345");
    assertThat(phone.cityCode()).isEqualTo("01");
    assertThat(phone.countryCode()).isEqualTo("+57");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "   ", "\t"})
  void eachMissingOrBlankFieldGivesItsOwnRequiredReason(String blank) {
    assertThat(Phone.violations(blank, CITY, COUNTRY))
        .containsExactly(Reason.PHONE_NUMBER_REQUIRED);
    assertThat(Phone.violations(NUMBER, blank, COUNTRY)).containsExactly(Reason.CITY_CODE_REQUIRED);
    assertThat(Phone.violations(NUMBER, CITY, blank)).containsExactly(Reason.COUNTRY_CODE_REQUIRED);
  }

  @Test
  void anEmptyPhoneReportsThreeReasonsAtOnce() {
    assertThat(Phone.violations(null, null, null))
        .containsExactlyInAnyOrder(
            Reason.PHONE_NUMBER_REQUIRED, Reason.CITY_CODE_REQUIRED, Reason.COUNTRY_CODE_REQUIRED);
  }

  @Test
  void valuesAtTheLimitAreAccepted() {
    assertThat(
            Phone.violations(
                "1".repeat(Phone.NUMBER_MAX_LENGTH),
                "2".repeat(Phone.CODE_MAX_LENGTH),
                "3".repeat(Phone.CODE_MAX_LENGTH)))
        .isEmpty();
    assertThat(Phone.NUMBER_MAX_LENGTH).isEqualTo(20);
    assertThat(Phone.CODE_MAX_LENGTH).isEqualTo(10);
  }

  @Test
  void eachFieldOverItsLimitGivesItsOwnTooLongReason() {
    String number = "1".repeat(Phone.NUMBER_MAX_LENGTH + 1);
    String code = "2".repeat(Phone.CODE_MAX_LENGTH + 1);

    assertThat(Phone.violations(number, CITY, COUNTRY))
        .containsExactly(Reason.PHONE_NUMBER_TOO_LONG);
    assertThat(Phone.violations(NUMBER, code, COUNTRY)).containsExactly(Reason.CITY_CODE_TOO_LONG);
    assertThat(Phone.violations(NUMBER, CITY, code)).containsExactly(Reason.COUNTRY_CODE_TOO_LONG);
  }

  @Test
  void aFiveThousandCharacterNumberIsRejectedByLength() {
    assertThat(Phone.violations("9".repeat(5_000), CITY, COUNTRY))
        .containsExactly(Reason.PHONE_NUMBER_TOO_LONG);
  }

  @Test
  void requiredWinsOverLengthBecauseABlankValueCannotAlsoBeTooLong() {
    assertThat(Phone.violations(" ".repeat(30), " ".repeat(30), " ".repeat(30)))
        .containsExactlyInAnyOrder(
            Reason.PHONE_NUMBER_REQUIRED, Reason.CITY_CODE_REQUIRED, Reason.COUNTRY_CODE_REQUIRED);
  }

  @Test
  void aMixOfMissingAndOversizedFieldsReportsOneReasonPerField() {
    assertThat(Phone.violations("1".repeat(21), null, "3".repeat(11)))
        .containsExactlyInAnyOrder(
            Reason.PHONE_NUMBER_TOO_LONG, Reason.CITY_CODE_REQUIRED, Reason.COUNTRY_CODE_TOO_LONG);
  }

  @Test
  void violationsArePureSoRepeatedCallsAgreeAndReturnIndependentSets() {
    Set<Reason> first = Phone.violations(null, CITY, COUNTRY);
    Set<Reason> second = Phone.violations(null, CITY, COUNTRY);

    assertThat(first).isNotSameAs(second).isEqualTo(second);
    assertThatThrownBy(() -> first.add(Reason.CITY_CODE_REQUIRED))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThat(Phone.violations(NUMBER, CITY, COUNTRY)).isEmpty();
  }

  @Test
  void theConstructorRejectsWhatTheRuleRejects() {
    assertThat(reasonsOf(() -> new Phone(null, "1".repeat(11), COUNTRY)))
        .containsExactlyInAnyOrder(Reason.PHONE_NUMBER_REQUIRED, Reason.CITY_CODE_TOO_LONG);
  }

  @Test
  void phonesWithTheSameValuesAreEqual() {
    assertThat(new Phone(NUMBER, CITY, COUNTRY))
        .isEqualTo(new Phone(NUMBER, CITY, COUNTRY))
        .hasSameHashCodeAs(new Phone(NUMBER, CITY, COUNTRY))
        .isNotEqualTo(new Phone(NUMBER, CITY, "56"));
  }
}
