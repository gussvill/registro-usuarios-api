package com.registro.usuarios.domain.policy;

import com.registro.usuarios.domain.exception.InvalidUserDataException.Reason;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Strategy for the format rule of a password, the rule most likely to be replaced.
 *
 * <p>Implementations only decide the format. The bounds that must hold whatever the format (the
 * password is required and fits in {@link #MAX_BYTES} UTF-8 bytes, the limit BCrypt works with) are
 * applied by {@link #violation(String)} before the format is consulted.
 */
public interface PasswordPolicy {

  int MAX_BYTES = 72;

  /** Format only; the caller guarantees a non-blank value within the length bounds. */
  boolean isSatisfiedBy(String rawPassword);

  /**
   * The first failing rule: required, then length, then format. A value longer than {@link
   * #MAX_BYTES} is rejected without encoding it, and never reaches the format rule.
   */
  default Optional<Reason> violation(String rawPassword) {
    if (rawPassword == null || rawPassword.isBlank()) {
      return Optional.of(Reason.PASSWORD_REQUIRED);
    }
    // A UTF-16 unit takes at least one byte in UTF-8, so more units than bytes allowed is enough.
    if (rawPassword.length() > MAX_BYTES
        || rawPassword.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
      return Optional.of(Reason.PASSWORD_TOO_LONG);
    }
    return isSatisfiedBy(rawPassword) ? Optional.empty() : Optional.of(Reason.PASSWORD_FORMAT);
  }
}
