package com.registro.usuarios.application.port;

import com.registro.usuarios.domain.exception.EmailAlreadyRegisteredException;
import com.registro.usuarios.domain.exception.InvalidUserDataException;
import com.registro.usuarios.domain.model.User;

/**
 * Puerto de entrada: lo que la aplicación ofrece a quien la dirige. Un punto de entrada (hoy el
 * adaptador web) depende de esta abstracción, que pertenece a la capa de aplicación, y nunca de la
 * clase que la implementa ({@code RegisterUserUseCase}).
 */
public interface RegisterUser {

  /**
   * Registra un nuevo usuario.
   *
   * @throws InvalidUserDataException si algún campo incumple una regla; se informan todos los
   *     campos incorrectos
   * @throws EmailAlreadyRegisteredException si el correo ya está en uso
   */
  User register(RegisterUserCommand command);
}
