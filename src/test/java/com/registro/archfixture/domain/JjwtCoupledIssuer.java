package com.registro.archfixture.domain;

import io.jsonwebtoken.Jwts;

/** Rompe "el dominio está libre de frameworks": la biblioteca JWT. */
public class JjwtCoupledIssuer {

  public String issue() {
    return Jwts.builder().subject("x").compact();
  }
}
