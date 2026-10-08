package com.registro.archfixture.domain;

import com.registro.archfixture.application.TransactionalUseCase;

/** Rompe el sentido de las dependencias: el dominio usa la capa de aplicación. */
public class ReachesApplication {

  public Object useCase() {
    return new TransactionalUseCase();
  }
}
