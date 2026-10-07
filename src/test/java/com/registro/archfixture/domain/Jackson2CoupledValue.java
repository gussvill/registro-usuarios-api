package com.registro.archfixture.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Breaks "the domain is free of frameworks": a Jackson annotation (the group id of both lines). */
public class Jackson2CoupledValue {

  @JsonProperty("value")
  @SuppressWarnings("unused")
  private String value;
}
