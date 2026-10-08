package com.registro.archfixture.infrastructure.security;

import com.registro.archfixture.infrastructure.web.CleanAdapter;

/** Rompe la independencia de los adaptadores: seguridad usa web. */
public class ReachesWeb {

  public Object web() {
    return new CleanAdapter();
  }
}
