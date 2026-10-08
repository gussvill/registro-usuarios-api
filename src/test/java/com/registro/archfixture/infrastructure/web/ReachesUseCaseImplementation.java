package com.registro.archfixture.infrastructure.web;

import com.registro.archfixture.application.TransactionalUseCase;

/** Breaks the inbound port: the web layer depends on the use case implementation itself. */
public class ReachesUseCaseImplementation {

  private final TransactionalUseCase useCase;

  public ReachesUseCaseImplementation(TransactionalUseCase useCase) {
    this.useCase = useCase;
  }

  public TransactionalUseCase useCase() {
    return useCase;
  }
}
