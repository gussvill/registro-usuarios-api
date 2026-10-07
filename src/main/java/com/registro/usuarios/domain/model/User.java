package com.registro.usuarios.domain.model;

import com.registro.usuarios.domain.exception.InvalidUserDataException.Reason;
import java.util.Optional;

/**
 * Aggregate root of a registered user.
 *
 * <p>This type is completed in the next work unit; for now it holds the limits that belong to the
 * aggregate and the rule for its name.
 */
public final class User {

  public static final int NAME_MAX_LENGTH = 255;
  public static final int MAX_PHONES = 10;

  private User() {}

  /** The first failing rule for a name: required, then length. */
  public static Optional<Reason> nameViolation(String name) {
    if (name == null || name.isBlank()) {
      return Optional.of(Reason.NAME_REQUIRED);
    }
    return name.length() > NAME_MAX_LENGTH ? Optional.of(Reason.NAME_TOO_LONG) : Optional.empty();
  }
}
