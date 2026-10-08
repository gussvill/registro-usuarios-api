package com.registro.archfixture.infrastructure.web;

import com.registro.archfixture.application.port.InboundPort;

/** A well-behaved web class: it reaches the application layer only through its inbound port. */
public class UsesInboundPort {

  private final InboundPort port;

  public UsesInboundPort(InboundPort port) {
    this.port = port;
  }

  public String call(String input) {
    return port.run(input);
  }
}
