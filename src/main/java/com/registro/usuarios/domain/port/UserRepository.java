package com.registro.usuarios.domain.port;

import com.registro.usuarios.domain.model.Email;
import com.registro.usuarios.domain.model.User;

/** Outbound port: where registered users are kept. Only what the registration needs. */
public interface UserRepository {

  boolean existsByEmail(Email email);

  /**
   * Stores a new user.
   *
   * @throws com.registro.usuarios.domain.exception.EmailAlreadyRegisteredException if the storage
   *     rejects the email as a duplicate, even when {@link #existsByEmail} said it was free
   */
  void save(User user);
}
