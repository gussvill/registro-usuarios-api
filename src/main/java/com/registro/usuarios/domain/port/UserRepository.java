package com.registro.usuarios.domain.port;

import com.registro.usuarios.domain.model.Email;
import com.registro.usuarios.domain.model.User;

/**
 * Puerto de salida: dónde se guardan los usuarios registrados. Solo lo que necesita el registro. Lo
 * implementa {@code UserPersistenceAdapter}.
 */
public interface UserRepository {

  boolean existsByEmail(Email email);

  /**
   * Guarda un nuevo usuario.
   *
   * @throws com.registro.usuarios.domain.exception.EmailAlreadyRegisteredException si el
   *     almacenamiento rechaza el correo por duplicado, incluso cuando {@link #existsByEmail} dijo
   *     que estaba libre
   */
  void save(User user);
}
