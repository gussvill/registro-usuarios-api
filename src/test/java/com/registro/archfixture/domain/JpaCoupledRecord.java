package com.registro.archfixture.domain;

import jakarta.persistence.Id;

/** Rompe "el dominio está libre de frameworks": una anotación de Jakarta Persistence. */
public class JpaCoupledRecord {

  @Id
  @SuppressWarnings("unused")
  private long id;
}
