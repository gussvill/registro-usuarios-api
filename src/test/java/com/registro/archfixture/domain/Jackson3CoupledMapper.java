package com.registro.archfixture.domain;

import tools.jackson.databind.json.JsonMapper;

/** Breaks "the domain is free of frameworks": a Jackson 3 class in the tools.jackson namespace. */
public class Jackson3CoupledMapper {

  @SuppressWarnings("unused")
  private final JsonMapper mapper = new JsonMapper();
}
