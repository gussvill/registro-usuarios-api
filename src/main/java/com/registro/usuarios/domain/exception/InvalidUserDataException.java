package com.registro.usuarios.domain.exception;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Uno o más campos de un registro fueron rechazados. Lleva {@link Reason}s tipados, nunca los
 * valores rechazados ni texto destinado al cliente: convertir un motivo en mensaje es presentación.
 */
public final class InvalidUserDataException extends DomainException {

  private static final long serialVersionUID = 1L;

  private final EnumSet<Reason> reasons;

  public InvalidUserDataException(Set<Reason> reasons) {
    super(describe(reasons));
    this.reasons = EnumSet.copyOf(reasons);
  }

  /** Los motivos distintos, como vista de solo lectura. */
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
