package com.registro.usuarios.domain.model;

/**
 * Why a field of a registration was rejected. A field has at most one reason: the first failing
 * rule, in the order required, then length, then format. A reason is a typed fact about the input:
 * it carries neither the rejected value nor any client-facing text.
 */
public enum Reason {
  NAME_REQUIRED,
  NAME_TOO_LONG,
  EMAIL_REQUIRED,
  EMAIL_TOO_LONG,
  EMAIL_FORMAT,
  PASSWORD_REQUIRED,
  PASSWORD_TOO_LONG,
  PASSWORD_FORMAT,
  PHONES_TOO_MANY,
  PHONE_NULL,
  PHONE_NUMBER_REQUIRED,
  PHONE_NUMBER_TOO_LONG,
  PHONE_NUMBER_FORMAT,
  CITY_CODE_REQUIRED,
  CITY_CODE_TOO_LONG,
  CITY_CODE_FORMAT,
  COUNTRY_CODE_REQUIRED,
  COUNTRY_CODE_TOO_LONG,
  COUNTRY_CODE_FORMAT
}
