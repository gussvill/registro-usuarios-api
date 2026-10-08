package com.registro.archfixture.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Rompe "el dominio está libre de frameworks": una anotación de Jackson (el group id de ambas
 * líneas).
 */
public class Jackson2CoupledValue {

  @JsonProperty("value")
  @SuppressWarnings("unused")
  private String value;
}
