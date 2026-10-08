package com.registro.archfixture.infrastructure.web;

import com.registro.archfixture.application.TransactionalUseCase;

/** Rompe el puerto de entrada: la capa web depende de la propia implementación del caso de uso. */
public class ReachesUseCaseImplementation {

  private final TransactionalUseCase useCase;

  public ReachesUseCaseImplementation(TransactionalUseCase useCase) {
    this.useCase = useCase;
  }

  public TransactionalUseCase useCase() {
    return useCase;
  }
}
