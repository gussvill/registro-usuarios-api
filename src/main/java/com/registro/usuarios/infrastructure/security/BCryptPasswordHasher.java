package com.registro.usuarios.infrastructure.security;

import com.registro.usuarios.domain.port.PasswordHasher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Implementa el puerto {@link PasswordHasher} con BCrypt de {@code spring-security-crypto}, que
 * aplica sal a cada hash. Solo se usa el módulo de criptografía, no la cadena de filtros de
 * seguridad.
 *
 * <p>El límite de 72 bytes de BCrypt lo impone el dominio antes de que una contraseña llegue aquí,
 * así que este adaptador no lo comprueba de nuevo.
 */
@Component
class BCryptPasswordHasher implements PasswordHasher {

  static final int DEFAULT_STRENGTH = 12;

  private final BCryptPasswordEncoder encoder;

  /** Fuerza de producción. */
  BCryptPasswordHasher() {
    this(DEFAULT_STRENGTH);
  }

  /**
   * Un costo menor para las pruebas, donde la lentitud deliberada de BCrypt solo añadiría espera.
   */
  BCryptPasswordHasher(int strength) {
    this.encoder = new BCryptPasswordEncoder(strength);
  }

  @Override
  public String hash(String rawPassword) {
    return encoder.encode(rawPassword);
  }
}
