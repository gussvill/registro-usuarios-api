package com.registro.usuarios.support;

import com.registro.usuarios.domain.port.PasswordHasher;
import java.util.ArrayList;
import java.util.List;

/**
 * Sustituto determinista e instantáneo de BCrypt. Su salida nunca contiene la contraseña en claro,
 * de modo que una prueba pueda demostrar que el valor en claro no se guardó.
 */
public final class FakePasswordHasher implements PasswordHasher {

  private final List<String> hashed = new ArrayList<>();

  @Override
  public String hash(String rawPassword) {
    hashed.add(rawPassword);
    return expectedHashOf(rawPassword);
  }

  /** El hash que devuelve este fake para una contraseña, sin registrar una llamada. */
  public static String expectedHashOf(String rawPassword) {
    return "fake-hash$" + Integer.toHexString(rawPassword.hashCode());
  }

  /** Las contraseñas en claro que se le pidió hashear, en orden de llamada. */
  public List<String> hashed() {
    return List.copyOf(hashed);
  }
}
