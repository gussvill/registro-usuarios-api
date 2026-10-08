package com.registro.usuarios.application.port;

import com.registro.usuarios.domain.model.PhoneInput;
import java.util.List;

/**
 * Entrada sin procesar del caso de uso de registro, tal como la recibió quien llama. Nada aquí está
 * validado: eso es trabajo del caso de uso, de modo que todo punto de entrada aplica las mismas
 * reglas.
 */
public record RegisterUserCommand(
    String name, String email, String password, List<PhoneData> phones) {

  /** Un teléfono tal como fue enviado. */
  public record PhoneData(String number, String cityCode, String countryCode)
      implements PhoneInput {}

  /** La contraseña nunca se imprime, esté presente o no. */
  @Override
  public String toString() {
    return "RegisterUserCommand[name="
        + name
        + ", email="
        + email
        + ", password=<redacted>, phones="
        + phones
        + "]";
  }
}
