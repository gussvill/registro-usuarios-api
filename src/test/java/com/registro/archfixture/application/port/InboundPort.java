package com.registro.archfixture.application.port;

/** La abstracción de la que un adaptador web puede depender. */
public interface InboundPort {

  String run(String input);
}
