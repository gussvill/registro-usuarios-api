package com.registro.usuarios.support;

import com.registro.usuarios.domain.port.PasswordHasher;
import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic, instantaneous stand-in for BCrypt. Its output never contains the raw password, so
 * a test can prove the raw value was not stored.
 */
public final class FakePasswordHasher implements PasswordHasher {

  private final List<String> hashed = new ArrayList<>();

  @Override
  public String hash(String rawPassword) {
    hashed.add(rawPassword);
    return expectedHashOf(rawPassword);
  }

  /** The hash this fake returns for a password, without recording a call. */
  public static String expectedHashOf(String rawPassword) {
    return "fake-hash$" + Integer.toHexString(rawPassword.hashCode());
  }

  /** The raw passwords it has been asked to hash, in call order. */
  public List<String> hashed() {
    return List.copyOf(hashed);
  }
}
