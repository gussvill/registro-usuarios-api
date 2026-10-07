package com.registro.archfixture.domain;

import jakarta.persistence.Id;

/** Breaks "the domain is free of frameworks": a Jakarta Persistence annotation. */
public class JpaCoupledRecord {

  @Id
  @SuppressWarnings("unused")
  private long id;
}
