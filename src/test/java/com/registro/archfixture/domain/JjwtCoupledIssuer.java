package com.registro.archfixture.domain;

import io.jsonwebtoken.Jwts;

/** Breaks "the domain is free of frameworks": the JWT library. */
public class JjwtCoupledIssuer {

  public String issue() {
    return Jwts.builder().subject("x").compact();
  }
}
