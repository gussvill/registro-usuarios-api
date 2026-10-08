package com.registro.archfixture.application;

import com.registro.archfixture.domain.CleanDomainType;
import org.springframework.transaction.annotation.Transactional;

/** Permitido: la capa de aplicación puede usar el dominio y la anotación de transacción. */
public class TransactionalUseCase {

  @Transactional
  public String run() {
    return new CleanDomainType().name();
  }
}
