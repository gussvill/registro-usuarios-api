package com.registro.usuarios.domain.model;

import java.util.Objects;
import java.util.UUID;

/** Identificador tipado de un usuario, para que no se pueda confundir con otras cadenas o UUID. */
public record UserId(UUID value) {

  public UserId {
    Objects.requireNonNull(value, "value");
  }

  /** Los identificadores los genera la aplicación, antes de que exista la fila. */
  public static UserId generate() {
    return new UserId(UUID.randomUUID());
  }
}
