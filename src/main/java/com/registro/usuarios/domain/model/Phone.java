package com.registro.usuarios.domain.model;

import com.registro.usuarios.domain.exception.InvalidUserDataException;
import com.registro.usuarios.domain.exception.InvalidUserDataException.Reason;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * A phone number with its city and country codes. The three parts are kept as strings, exactly as
 * received, so leading zeros and a leading {@code +} survive.
 */
public record Phone(String number, String cityCode, String countryCode) {

  public static final int NUMBER_MAX_LENGTH = 20;
  public static final int CODE_MAX_LENGTH = 10;

  public Phone {
    Set<Reason> violations = violations(number, cityCode, countryCode);
    if (!violations.isEmpty()) {
      throw new InvalidUserDataException(violations);
    }
  }

  /**
   * Every field that breaks a rule, at most one reason per field: the field is either missing or
   * too long. The result is a new read-only set on each call.
   */
  public static Set<Reason> violations(String number, String cityCode, String countryCode) {
    EnumSet<Reason> reasons = EnumSet.noneOf(Reason.class);
    check(number, NUMBER_MAX_LENGTH, Reason.PHONE_NUMBER_REQUIRED, Reason.PHONE_NUMBER_TOO_LONG)
        .ifPresent(reasons::add);
    check(cityCode, CODE_MAX_LENGTH, Reason.CITY_CODE_REQUIRED, Reason.CITY_CODE_TOO_LONG)
        .ifPresent(reasons::add);
    check(countryCode, CODE_MAX_LENGTH, Reason.COUNTRY_CODE_REQUIRED, Reason.COUNTRY_CODE_TOO_LONG)
        .ifPresent(reasons::add);
    return Collections.unmodifiableSet(reasons);
  }

  private static Optional<Reason> check(
      String value, int maxLength, Reason required, Reason tooLong) {
    if (value == null || value.isBlank()) {
      return Optional.of(required);
    }
    return value.length() > maxLength ? Optional.of(tooLong) : Optional.empty();
  }
}
