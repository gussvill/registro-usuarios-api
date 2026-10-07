package com.registro.archfixture.application;

import com.registro.archfixture.infrastructure.web.CleanAdapter;

/** Breaks the direction of dependencies: the application layer uses an adapter. */
public class ReachesInfrastructure {

  public Object adapter() {
    return new CleanAdapter();
  }
}
