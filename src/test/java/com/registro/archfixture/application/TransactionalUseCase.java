package com.registro.archfixture.application;

import com.registro.archfixture.domain.CleanDomainType;
import org.springframework.transaction.annotation.Transactional;

/** Allowed: the application layer may use the domain and the transaction annotation. */
public class TransactionalUseCase {

  @Transactional
  public String run() {
    return new CleanDomainType().name();
  }
}
