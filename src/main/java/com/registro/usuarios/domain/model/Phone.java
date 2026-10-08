package com.registro.usuarios.domain.model;

import com.registro.usuarios.domain.exception.InvalidUserDataException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A phone number with its city and country codes. The three parts are kept as strings, exactly as
 * received, so leading zeros and a leading {@code +} survive. The number and the city code are
 * ASCII digits only; the country code is ASCII digits with an optional leading {@code +}.
 */
public record Phone(String number, String cityCode, String countryCode) {

  public static final int NUMBER_MAX_LENGTH = 20;
  public static final int CODE_MAX_LENGTH = 10;

  /** The number and the city code: one or more ASCII digits. */
  public static final String DIGITS_PATTERN = "^[0-9]+$";

  /** The country code: one or more ASCII digits, optionally preceded by a single {@code +}. */
  public static final String COUNTRY_CODE_PATTERN = "^\\+?[0-9]+$";

  private static final Pattern DIGITS = Pattern.compile(DIGITS_PATTERN);
  private static final Pattern COUNTRY_CODE = Pattern.compile(COUNTRY_CODE_PATTERN);

  public Phone {
    Set<Reason> violations = violations(number, cityCode, countryCode);
    if (!violations.isEmpty()) {
      throw new InvalidUserDataException(violations);
    }
  }

  /**
   * Every field that breaks a rule, at most one reason per field: the first failing rule of
   * required, then length, then format. The result is a new read-only set on each call.
   */
  public static Set<Reason> violations(String number, String cityCode, String countryCode) {
    EnumSet<Reason> reasons = EnumSet.noneOf(Reason.class);
    check(
            number,
            NUMBER_MAX_LENGTH,
            DIGITS,
            Reason.PHONE_NUMBER_REQUIRED,
            Reason.PHONE_NUMBER_TOO_LONG,
            Reason.PHONE_NUMBER_FORMAT)
        .ifPresent(reasons::add);
    check(
            cityCode,
            CODE_MAX_LENGTH,
            DIGITS,
            Reason.CITY_CODE_REQUIRED,
            Reason.CITY_CODE_TOO_LONG,
            Reason.CITY_CODE_FORMAT)
        .ifPresent(reasons::add);
    check(
            countryCode,
            CODE_MAX_LENGTH,
            COUNTRY_CODE,
            Reason.COUNTRY_CODE_REQUIRED,
            Reason.COUNTRY_CODE_TOO_LONG,
            Reason.COUNTRY_CODE_FORMAT)
        .ifPresent(reasons::add);
    return Collections.unmodifiableSet(reasons);
  }

  private static Optional<Reason> check(
      String value,
      int maxLength,
      Pattern format,
      Reason required,
      Reason tooLong,
      Reason malformed) {
    if (value == null || value.isBlank()) {
      return Optional.of(required);
    }
    if (value.length() > maxLength) {
      return Optional.of(tooLong);
    }
    return format.matcher(value).matches() ? Optional.empty() : Optional.of(malformed);
  }
}
