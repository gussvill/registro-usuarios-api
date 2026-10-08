package com.registro.usuarios.domain.policy;

import com.registro.usuarios.domain.model.Reason;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;

/**
 * The rules for a password that hold whatever the configured format: it is required and fits in
 * {@link #MAX_BYTES} UTF-8 bytes, the limit BCrypt works with.
 *
 * <p>These limits live in a final class with a static function, not in {@link PasswordPolicy}, so
 * that no implementation of the replaceable format rule can bypass them.
 */
public final class Password {

  public static final int MAX_BYTES = 72;

  private Password() {}

  /**
   * The first failing rule: required, then length, then the format of {@code format}. A value
   * longer than {@link #MAX_BYTES} is rejected without encoding it, and never reaches the format
   * rule.
   */
  public static Optional<Reason> violation(String rawPassword, PasswordPolicy format) {
    Objects.requireNonNull(format, "format");
    if (rawPassword == null || rawPassword.isBlank()) {
      return Optional.of(Reason.PASSWORD_REQUIRED);
    }
    // A UTF-16 unit takes at least one byte in UTF-8, so more units than bytes allowed is enough.
    if (rawPassword.length() > MAX_BYTES
        || rawPassword.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
      return Optional.of(Reason.PASSWORD_TOO_LONG);
    }
    return format.isSatisfiedBy(rawPassword)
        ? Optional.empty()
        : Optional.of(Reason.PASSWORD_FORMAT);
  }
}
