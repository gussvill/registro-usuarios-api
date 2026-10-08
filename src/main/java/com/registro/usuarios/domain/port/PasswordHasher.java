package com.registro.usuarios.domain.port;

/**
 * Puerto de salida: hash unidireccional y con sal de una contraseña. Lo implementa {@code
 * BCryptPasswordHasher}.
 */
public interface PasswordHasher {

  String hash(String rawPassword);
}
