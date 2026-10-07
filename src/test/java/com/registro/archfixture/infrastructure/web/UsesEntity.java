package com.registro.archfixture.infrastructure.web;

import com.registro.archfixture.infrastructure.persistence.StoredEntity;

/** Breaks "entities do not cross the web layer": a web class that uses a JPA entity. */
public class UsesEntity {

  public StoredEntity leak() {
    return new StoredEntity();
  }
}
