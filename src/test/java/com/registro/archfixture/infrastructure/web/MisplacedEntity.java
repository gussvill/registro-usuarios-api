package com.registro.archfixture.infrastructure.web;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** Rompe "las entidades viven en el paquete de persistencia": una entidad en el paquete web. */
@Entity
public class MisplacedEntity {

  @Id
  @SuppressWarnings("unused")
  private long id;
}
