package com.registro.usuarios.domain.model;

import com.registro.usuarios.domain.exception.InvalidUserDataException;
import com.registro.usuarios.domain.exception.Reason;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Un número de teléfono con su código de ciudad y de país. Las tres partes se conservan como
 * cadenas, tal como se recibieron, de modo que se preservan los ceros iniciales y un {@code +}
 * inicial. El número y el código de ciudad son solo dígitos ASCII; el código de país son dígitos
 * ASCII con un {@code +} inicial opcional.
 */
public record Phone(String number, String cityCode, String countryCode) {

  public static final int NUMBER_MAX_LENGTH = 20;
  public static final int CODE_MAX_LENGTH = 10;

  /** El número y el código de ciudad: uno o más dígitos ASCII. */
  public static final String DIGITS_PATTERN = "^[0-9]+$";

  /**
   * El código de país: uno o más dígitos ASCII, opcionalmente precedidos por un único {@code +}.
   */
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
   * Todos los campos que incumplen una regla, como máximo un motivo por campo: la primera regla
   * incumplida entre obligatorio, longitud y formato. El resultado es un conjunto nuevo de solo
   * lectura en cada llamada.
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
