package com.registro.archfixture.infrastructure.web;

import com.registro.archfixture.application.port.InboundPort;

/**
 * Una clase web bien comportada: llega a la capa de aplicación solo a través de su puerto de
 * entrada.
 */
public class UsesInboundPort {

  private final InboundPort port;

  public UsesInboundPort(InboundPort port) {
    this.port = port;
  }

  public String call(String input) {
    return port.run(input);
  }
}
