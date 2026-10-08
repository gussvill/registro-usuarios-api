package com.registro.usuarios.domain.policy;

/**
 * Strategy for the format rule of a password, the rule most likely to be replaced.
 *
 * <p>An implementation decides the format and nothing else. The bounds that must hold whatever the
 * format (required, at most {@link Password#MAX_BYTES} bytes) are applied by {@link
 * Password#violation} before the format is consulted, and an implementation cannot change them.
 */
public interface PasswordPolicy {

  /** Format only; the caller guarantees a non-blank value within the length bounds. */
  boolean isSatisfiedBy(String rawPassword);
}
