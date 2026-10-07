package com.registro.usuarios.domain.exception;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * One or more fields of a registration were rejected. It carries typed {@link Reason}s, never the
 * rejected values and never client-facing text: turning a reason into a message is presentation.
 */
public final class InvalidUserDataException extends DomainException {

  private static final long serialVersionUID = 1L;

  /** Why a field was rejected; at most one reason per field, the first failing rule. */
  public enum Reason {
    NAME_REQUIRED,
    NAME_TOO_LONG,
    EMAIL_REQUIRED,
    EMAIL_TOO_LONG,
    EMAIL_FORMAT,
    PASSWORD_REQUIRED,
    PASSWORD_TOO_LONG,
    PASSWORD_FORMAT,
    PHONES_TOO_MANY,
    PHONE_NULL,
    PHONE_NUMBER_REQUIRED,
    PHONE_NUMBER_TOO_LONG,
    CITY_CODE_REQUIRED,
    CITY_CODE_TOO_LONG,
    COUNTRY_CODE_REQUIRED,
    COUNTRY_CODE_TOO_LONG
  }

  private final EnumSet<Reason> reasons;

  public InvalidUserDataException(Set<Reason> reasons) {
    super(describe(reasons));
    this.reasons = EnumSet.copyOf(reasons);
  }

  /** The distinct reasons, as a read-only view. */
  public Set<Reason> reasons() {
    return Collections.unmodifiableSet(reasons);
  }

  private static String describe(Set<Reason> reasons) {
    if (reasons.isEmpty()) {
      throw new IllegalArgumentException("A rejection needs at least one reason");
    }
    return "Invalid user data: " + EnumSet.copyOf(reasons);
  }
}
