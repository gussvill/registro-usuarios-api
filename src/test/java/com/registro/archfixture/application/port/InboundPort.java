package com.registro.archfixture.application.port;

/** The abstraction a web adapter is allowed to depend on. */
public interface InboundPort {

  String run(String input);
}
