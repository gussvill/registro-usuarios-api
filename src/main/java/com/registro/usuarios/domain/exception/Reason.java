package com.registro.usuarios.domain.exception;

/**
 * Por qué se rechazó un campo de un registro. Un campo tiene como máximo un motivo: la primera
 * regla incumplida, en este orden: obligatorio, longitud y formato. Un motivo es un hecho tipado
 * sobre la entrada: no lleva ni el valor rechazado ni texto destinado al cliente.
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
