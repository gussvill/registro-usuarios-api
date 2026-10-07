package com.registro.archfixture.infrastructure.web;

import com.registro.archfixture.infrastructure.persistence.StoredThing;

/** Breaks the independence of adapters: web uses persistence. */
public class ReachesPersistence {

  public Object stored() {
    return new StoredThing();
  }
}
