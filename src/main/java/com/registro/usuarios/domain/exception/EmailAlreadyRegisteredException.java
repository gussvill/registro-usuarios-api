package com.registro.usuarios.domain.exception;

/** El correo ya está en uso. Deliberadamente no lleva ni la dirección ni ningún otro dato. */
public final class EmailAlreadyRegisteredException extends DomainException {

  private static final long serialVersionUID = 1L;

  public EmailAlreadyRegisteredException() {
    super("Email already registered");
  }
}
