package com.registro.archfixture.cycle.ledger;

import com.registro.archfixture.cycle.billing.Invoice;

/** Rompe "sin ciclos entre paquetes": este paquete usa a {@code billing}, que a su vez lo usa. */
public class LedgerEntry {

  public Invoice invoice() {
    return new Invoice();
  }
}
