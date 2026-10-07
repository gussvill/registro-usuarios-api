package com.registro.usuarios.domain.exception;

/** Base type of every business rejection. It carries no client-facing text. */
public abstract class DomainException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  protected DomainException(String message) {
    super(message);
  }
}
