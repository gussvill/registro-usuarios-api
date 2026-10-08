package com.registro.usuarios.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The rules for a submitted list of phones as a whole: who may be absent, how many, which entry.
 */
class PhoneListRuleTest {

  private record Submitted(String number, String cityCode, String countryCode)
      implements PhoneInput {}

  private static Submitted phone(String number, String cityCode, String countryCode) {
    return new Submitted(number, cityCode, countryCode);
  }

  @Test
  void anAbsentListBreaksNoRule() {
    assertThat(User.phoneListViolations(null)).isEmpty();
  }

  @Test
  void anEmptyListBreaksNoRule() {
    assertThat(User.phoneListViolations(List.of())).isEmpty();
  }

  @Test
  void validEntriesBreakNoRule() {
    assertThat(
            User.phoneListViolations(
                List.of(phone("1234567", "1", "57"), phone("7654321", "2", "+56"))))
        .isEmpty();
  }

  @Test
  void aNullEntryIsReportedAsNull() {
    assertThat(User.phoneListViolations(Arrays.asList((PhoneInput) null)))
        .containsExactly(Reason.PHONE_NULL);
  }

  @Test
  void aNullEntryDoesNotHideTheViolationsOfItsNeighbours() {
    List<PhoneInput> phones =
        Arrays.asList(phone("1234567", "1", "57"), null, phone(null, "1", "57"));

    assertThat(User.phoneListViolations(phones))
        .containsExactlyInAnyOrder(Reason.PHONE_NULL, Reason.PHONE_NUMBER_REQUIRED);
  }

  @Test
  void everyFieldOfAnEntryReportsItsOwnReason() {
    assertThat(User.phoneListViolations(List.of(phone(null, null, null))))
        .containsExactlyInAnyOrder(
            Reason.PHONE_NUMBER_REQUIRED, Reason.CITY_CODE_REQUIRED, Reason.COUNTRY_CODE_REQUIRED);
  }

  @Test
  void theSameViolationInTwoEntriesCollapsesIntoOneReason() {
    assertThat(User.phoneListViolations(List.of(phone(null, "1", "57"), phone(" ", "2", "56"))))
        .containsExactly(Reason.PHONE_NUMBER_REQUIRED);
  }

  @Test
  void tenEntriesAreWithinTheLimit() {
    List<Submitted> ten = Stream.generate(() -> phone("1234567", "1", "57")).limit(10).toList();

    assertThat(User.phoneListViolations(ten)).isEmpty();
  }

  @Test
  void elevenEntriesAreTooManyAndNoEntryIsInspected() {
    List<PhoneInput> elevenNulls = Arrays.asList(new PhoneInput[11]);
    List<PhoneInput> elevenInvalid =
        Stream.generate(() -> (PhoneInput) phone(null, null, null)).limit(11).toList();

    assertThat(User.phoneListViolations(elevenNulls)).containsExactly(Reason.PHONES_TOO_MANY);
    assertThat(User.phoneListViolations(elevenInvalid)).containsExactly(Reason.PHONES_TOO_MANY);
  }

  @Test
  void theResultIsAReadOnlySetOfDistinctReasons() {
    Set<Reason> reasons = User.phoneListViolations(List.of(phone(null, "1", "57")));

    assertThatThrownBy(() -> reasons.add(Reason.PHONE_NULL))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
