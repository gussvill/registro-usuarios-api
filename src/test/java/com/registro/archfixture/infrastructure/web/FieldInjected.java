package com.registro.archfixture.infrastructure.web;

import org.springframework.beans.factory.annotation.Autowired;

/** Breaks "no field injection". */
public class FieldInjected {

  @Autowired
  @SuppressWarnings("unused")
  private CleanAdapter adapter;
}
