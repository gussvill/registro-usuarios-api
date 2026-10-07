package com.registro.archfixture.infrastructure.security;

import com.registro.archfixture.infrastructure.web.CleanAdapter;

/** Breaks the independence of adapters: security uses web. */
public class ReachesWeb {

  public Object web() {
    return new CleanAdapter();
  }
}
