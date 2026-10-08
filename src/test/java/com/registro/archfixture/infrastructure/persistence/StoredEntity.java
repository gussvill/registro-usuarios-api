package com.registro.archfixture.infrastructure.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** Una entidad bien ubicada: el destino de una dependencia prohibida desde el fixture web. */
@Entity
public class StoredEntity {

  @Id
  @SuppressWarnings("unused")
  private long id;
}
