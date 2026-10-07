package com.registro.archfixture.infrastructure.web;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** Breaks "entities live in the persistence package": an entity in the web package. */
@Entity
public class MisplacedEntity {

  @Id
  @SuppressWarnings("unused")
  private long id;
}
