package com.registro.usuarios.domain.model;

import com.registro.usuarios.domain.exception.InvalidUserDataException;
import com.registro.usuarios.domain.exception.InvalidUserDataException.Reason;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * An email address, normalised so that equality by value is case-insensitive.
 *
 * <p>The value is lower-cased with {@link Locale#ROOT} and never trimmed. The format is a {@link
 * Pattern} supplied by the caller (it is configuration), applied to the lower-cased value with
 * {@code matches()} so that the whole input must be consumed.
 */
public final class Email {

  public static final int MAX_LENGTH = 254;

  private final String value;

  private Email(String value) {
    this.value = value;
  }

  /**
   * The first failing rule, in the order required, then length, then format. The length is checked
   * before the pattern so an oversized value never reaches the regular expression engine.
   */
  public static Optional<Reason> violation(String raw, Pattern format) {
    Objects.requireNonNull(format, "format");
    if (raw == null || raw.isBlank()) {
      return Optional.of(Reason.EMAIL_REQUIRED);
    }
    String normalised = normalise(raw);
    if (normalised.length() > MAX_LENGTH) {
      return Optional.of(Reason.EMAIL_TOO_LONG);
    }
    if (!format.matcher(normalised).matches()) {
      return Optional.of(Reason.EMAIL_FORMAT);
    }
    return Optional.empty();
  }

  /** Builds a valid email or rejects with the reason of {@link #violation}. */
  public static Email of(String raw, Pattern format) {
    violation(raw, format)
        .ifPresent(
            reason -> {
              throw new InvalidUserDataException(Set.of(reason));
            });
    return new Email(normalise(raw));
  }

  public String value() {
    return value;
  }

  /** Safe for logs: keeps the domain and hides the local part. */
  public String masked() {
    int at = value.indexOf('@');
    return at < 0 ? "***" : "***" + value.substring(at);
  }

  private static String normalise(String raw) {
    return raw.toLowerCase(Locale.ROOT);
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof Email email && value.equals(email.value);
  }

  @Override
  public int hashCode() {
    return value.hashCode();
  }

  /** Never prints the full address, so an accidental log line stays harmless. */
  @Override
  public String toString() {
    return masked();
  }
}
