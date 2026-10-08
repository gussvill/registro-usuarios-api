package com.registro.archfixture.application;

import com.registro.archfixture.infrastructure.web.CleanAdapter;

/** Rompe el sentido de las dependencias: la capa de aplicación usa un adaptador. */
public class ReachesInfrastructure {

  public Object adapter() {
    return new CleanAdapter();
  }
}
