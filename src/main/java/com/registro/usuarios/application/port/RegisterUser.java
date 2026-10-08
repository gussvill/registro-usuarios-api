package com.registro.usuarios.application.port;

import com.registro.usuarios.domain.exception.EmailAlreadyRegisteredException;
import com.registro.usuarios.domain.exception.InvalidUserDataException;
import com.registro.usuarios.domain.model.User;

/**
 * Inbound port: what the application offers to whoever drives it. An entry point (today the web
 * adapter) depends on this abstraction, which the application layer owns, and never on the class
 * that implements it.
 */
public interface RegisterUser {

  /**
   * Registers a new user.
   *
   * @throws InvalidUserDataException if any field breaks a rule; every broken field is reported
   * @throws EmailAlreadyRegisteredException if the email is taken
   */
  User register(RegisterUserCommand command);
}
