package com.registro.usuarios.domain.policy;

/**
 * Estrategia para la regla de formato de una contraseña, la regla con más probabilidad de ser
 * reemplazada. La implementa {@code RegexPasswordPolicy}.
 *
 * <p>Una implementación decide el formato y nada más. Los límites que deben cumplirse sea cual sea
 * el formato (obligatoria, como máximo {@link Password#MAX_BYTES} bytes) los aplica {@link
 * Password#violation} antes de consultar el formato, y una implementación no puede cambiarlos.
 */
public interface PasswordPolicy {

  /**
   * Solo el formato; quien llama garantiza un valor no vacío y dentro de los límites de longitud.
   */
  boolean isSatisfiedBy(String rawPassword);
}
