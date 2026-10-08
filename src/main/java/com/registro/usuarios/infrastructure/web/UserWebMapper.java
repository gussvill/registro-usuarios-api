package com.registro.usuarios.infrastructure.web;

import com.registro.usuarios.application.port.RegisterUserCommand;
import com.registro.usuarios.application.port.RegisterUserCommand.PhoneData;
import com.registro.usuarios.domain.model.Phone;
import com.registro.usuarios.domain.model.User;
import java.util.ArrayList;
import java.util.List;

/**
 * Traduce entre los records JSON y los tipos de la aplicación. Manual a propósito: ambos lados son
 * records, así que un campo olvidado es un error de compilación. La petición se pasa tal como se
 * recibió, con los valores ausentes sin tocar, porque decidir qué es aceptable es trabajo del
 * dominio. La respuesta se construye a partir del agregado y nunca lee el hash de la contraseña.
 */
final class UserWebMapper {

  private UserWebMapper() {}

  static RegisterUserCommand toCommand(RegisterUserRequest request) {
    return new RegisterUserCommand(
        request.name(), request.email(), request.password(), phonesOf(request.phones()));
  }

  static UserResponse toResponse(User user) {
    return new UserResponse(
        user.id().value(),
        user.name(),
        user.email().value(),
        user.phones().stream().map(UserWebMapper::toResponse).toList(),
        user.created(),
        user.modified(),
        user.lastLogin(),
        user.token(),
        user.active());
  }

  private static PhoneResponse toResponse(Phone phone) {
    return new PhoneResponse(phone.number(), phone.cityCode(), phone.countryCode());
  }

  /**
   * Una lista nula y una entrada nula siguen siendo nulas: el dominio las informa como errores de
   * entrada.
   */
  private static List<PhoneData> phonesOf(List<PhoneRequest> phones) {
    if (phones == null) {
      return null;
    }
    List<PhoneData> mapped = new ArrayList<>(phones.size());
    for (PhoneRequest phone : phones) {
      mapped.add(
          phone == null
              ? null
              : new PhoneData(phone.number(), phone.citycode(), phone.countryCode()));
    }
    return mapped;
  }
}
