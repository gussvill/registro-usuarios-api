package com.registro.usuarios.domain.port;

/** Outbound port: one-way, salted hashing of a password. */
public interface PasswordHasher {

  String hash(String rawPassword);
}
