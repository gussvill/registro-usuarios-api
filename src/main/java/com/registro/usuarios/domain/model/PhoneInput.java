package com.registro.usuarios.domain.model;

/**
 * Las tres partes de un teléfono tal como las envió quien llama, antes de que ninguna regla las
 * juzgue. Permite al dominio comprobar una lista de teléfonos enviados sin saber en qué tipo los
 * guarda quien llama. Lo implementa {@code RegisterUserCommand.PhoneData}.
 */
public interface PhoneInput {

  String number();

  String cityCode();

  String countryCode();
}
