package com.registro.usuarios.domain.policy;

import com.registro.usuarios.domain.model.Reason;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;

/**
 * Las reglas de una contraseña que se cumplen sea cual sea el formato configurado: es obligatoria y
 * cabe en {@link #MAX_BYTES} bytes UTF-8, el límite con el que trabaja BCrypt.
 *
 * <p>Estos límites viven en una clase final con una función estática, y no en {@link
 * PasswordPolicy}, para que ninguna implementación de la regla de formato reemplazable pueda
 * eludirlos.
 */
public final class Password {

  public static final int MAX_BYTES = 72;

  private Password() {}

  /**
   * La primera regla incumplida: obligatoria, longitud y luego el formato de {@code format}. Un
   * valor más largo que {@link #MAX_BYTES} se rechaza sin codificarlo y nunca llega a la regla de
   * formato.
   */
  public static Optional<Reason> violation(String rawPassword, PasswordPolicy format) {
    Objects.requireNonNull(format, "format");
    if (rawPassword == null || rawPassword.isBlank()) {
      return Optional.of(Reason.PASSWORD_REQUIRED);
    }
    // Una unidad UTF-16 ocupa al menos un byte en UTF-8, así que basta con que haya más unidades
    // que bytes permitidos.
    if (rawPassword.length() > MAX_BYTES
        || rawPassword.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
      return Optional.of(Reason.PASSWORD_TOO_LONG);
    }
    return format.isSatisfiedBy(rawPassword)
        ? Optional.empty()
        : Optional.of(Reason.PASSWORD_FORMAT);
  }
}
