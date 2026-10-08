package com.registro.usuarios.domain.policy;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Un formato de contraseña definido por una expresión regular, que es configuración del operador.
 */
public final class RegexPasswordPolicy implements PasswordPolicy {

  private final Pattern pattern;

  public RegexPasswordPolicy(Pattern pattern) {
    this.pattern = Objects.requireNonNull(pattern, "pattern");
  }

  @Override
  public boolean isSatisfiedBy(String rawPassword) {
    return rawPassword != null && pattern.matcher(rawPassword).matches();
  }
}
