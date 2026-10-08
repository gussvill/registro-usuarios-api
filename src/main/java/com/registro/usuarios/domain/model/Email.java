package com.registro.usuarios.domain.model;

import com.registro.usuarios.domain.exception.InvalidUserDataException;
import com.registro.usuarios.domain.exception.Reason;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Una dirección de correo, normalizada para que la igualdad por valor no distinga mayúsculas.
 *
 * <p>El valor se pasa a minúsculas con {@link Locale#ROOT} y nunca se recorta. El formato es un
 * {@link Pattern} que aporta quien llama (es configuración), aplicado al valor en minúsculas con
 * {@code matches()} para que se consuma toda la entrada.
 */
public final class Email {

  public static final int MAX_LENGTH = 254;

  private final String value;

  private Email(String value) {
    this.value = value;
  }

  /**
   * La primera regla incumplida, en este orden: obligatorio, longitud y formato. La longitud se
   * comprueba antes que el patrón para que un valor demasiado largo nunca llegue al motor de
   * expresiones regulares.
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

  /** Construye un correo válido o lo rechaza con el motivo de {@link #violation}. */
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

  /** Seguro para los logs: conserva el dominio y oculta la parte local. */
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

  /**
   * Nunca imprime la dirección completa, de modo que una línea de log accidental sea inofensiva.
   */
  @Override
  public String toString() {
    return masked();
  }
}
