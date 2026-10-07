package com.registro.archfixture.infrastructure.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** A well placed entity: the target of a forbidden dependency from the web fixture. */
@Entity
public class StoredEntity {

  @Id
  @SuppressWarnings("unused")
  private long id;
}
