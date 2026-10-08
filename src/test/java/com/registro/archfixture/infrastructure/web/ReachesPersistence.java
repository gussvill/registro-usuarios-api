package com.registro.archfixture.infrastructure.web;

import com.registro.archfixture.infrastructure.persistence.StoredThing;

/** Rompe la independencia de los adaptadores: web usa persistencia. */
public class ReachesPersistence {

  public Object stored() {
    return new StoredThing();
  }
}
