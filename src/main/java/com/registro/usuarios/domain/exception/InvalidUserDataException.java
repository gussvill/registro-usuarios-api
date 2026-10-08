package com.registro.usuarios.domain.exception;

import com.registro.usuarios.domain.model.Reason;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * One or more fields of a registration were rejected. It carries typed {@link Reason}s, never the
 * rejected values and never client-facing text: turning a reason into a message is presentation.
 */
public final class InvalidUserDataException extends DomainException {

  private static final long serialVersionUID = 1L;

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
