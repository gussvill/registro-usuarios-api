package com.registro.usuarios.domain.exception;

/** The email is already taken. It deliberately carries neither the address nor any other data. */
public final class EmailAlreadyRegisteredException extends DomainException {

  private static final long serialVersionUID = 1L;

  public EmailAlreadyRegisteredException() {
    super("Email already registered");
  }
}
