package com.registro.archfixture.domain;

import tools.jackson.databind.json.JsonMapper;

/**
 * Rompe "el dominio está libre de frameworks": una clase de Jackson 3 en el espacio de nombres
 * tools.jackson.
 */
public class Jackson3CoupledMapper {

  @SuppressWarnings("unused")
  private final JsonMapper mapper = new JsonMapper();
}
