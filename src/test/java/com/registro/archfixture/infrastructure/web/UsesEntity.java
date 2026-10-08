package com.registro.archfixture.infrastructure.web;

import com.registro.archfixture.infrastructure.persistence.StoredEntity;

/** Rompe "las entidades no cruzan la capa web": una clase web que usa una entidad JPA. */
public class UsesEntity {

  public StoredEntity leak() {
    return new StoredEntity();
  }
}
