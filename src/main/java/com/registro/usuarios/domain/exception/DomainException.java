package com.registro.usuarios.domain.exception;

/**
 * Tipo base de todo rechazo de negocio. No lleva texto destinado al cliente. Lo extienden {@code
 * InvalidUserDataException} y {@code EmailAlreadyRegisteredException}.
 */
public abstract class DomainException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  protected DomainException(String message) {
    super(message);
  }
}
