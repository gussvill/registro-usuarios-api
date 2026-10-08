package com.registro.usuarios.application;

import com.registro.usuarios.domain.model.PhoneInput;
import java.util.List;

/**
 * Raw input of the registration use case, exactly as the caller received it. Nothing here is
 * validated: that is the use case's job, so every entry point gets the same rules.
 */
public record RegisterUserCommand(
    String name, String email, String password, List<PhoneData> phones) {

  /** One phone as submitted. */
  public record PhoneData(String number, String cityCode, String countryCode)
      implements PhoneInput {}

  /** The password is never printed, whether or not it is present. */
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
