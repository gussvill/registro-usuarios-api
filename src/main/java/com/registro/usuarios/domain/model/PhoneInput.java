package com.registro.usuarios.domain.model;

/**
 * The three parts of a phone as a caller submitted them, before any rule has judged them. It lets
 * the domain check a list of submitted phones without knowing which type the caller keeps them in.
 */
public interface PhoneInput {

  String number();

  String cityCode();

  String countryCode();
}
