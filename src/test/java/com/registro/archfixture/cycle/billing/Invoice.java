package com.registro.archfixture.cycle.billing;

import com.registro.archfixture.cycle.ledger.LedgerEntry;

/** Cierra el ciclo con {@code ledger}: dos paquetes que se necesitan mutuamente. */
public class Invoice {

  public LedgerEntry entry() {
    return new LedgerEntry();
  }
}
