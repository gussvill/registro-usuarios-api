package com.registro.usuarios.infrastructure.security;

import com.registro.usuarios.domain.port.PasswordHasher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Implements the {@link PasswordHasher} port with BCrypt from {@code spring-security-crypto}, which
 * salts every hash. Only the crypto module is used, not the security filter chain.
 *
 * <p>The 72-byte limit of BCrypt is enforced by the domain before a password gets here, so this
 * adapter does not check it again.
 */
@Component
class BCryptPasswordHasher implements PasswordHasher {

  static final int DEFAULT_STRENGTH = 12;

  private final BCryptPasswordEncoder encoder;

  /** Production strength. */
  BCryptPasswordHasher() {
    this(DEFAULT_STRENGTH);
  }

  /** A lower cost for tests, where BCrypt's deliberate slowness would only add waiting. */
  BCryptPasswordHasher(int strength) {
    this.encoder = new BCryptPasswordEncoder(strength);
  }

  @Override
  public String hash(String rawPassword) {
    return encoder.encode(rawPassword);
  }
}
