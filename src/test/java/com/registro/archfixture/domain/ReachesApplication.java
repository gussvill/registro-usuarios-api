package com.registro.archfixture.domain;

import com.registro.archfixture.application.TransactionalUseCase;

/** Breaks the direction of dependencies: the domain uses the application layer. */
public class ReachesApplication {

  public Object useCase() {
    return new TransactionalUseCase();
  }
}
