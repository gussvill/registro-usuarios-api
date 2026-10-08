package com.registro.archfixture.infrastructure.web;

import org.springframework.beans.factory.annotation.Autowired;

/** Rompe "sin inyección por campo". */
public class FieldInjected {

  @Autowired
  @SuppressWarnings("unused")
  private CleanAdapter adapter;
}
