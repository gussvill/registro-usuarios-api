package com.registro.usuarios.infrastructure.web;

import com.registro.usuarios.application.port.RegisterUserCommand;
import com.registro.usuarios.application.port.RegisterUserCommand.PhoneData;
import com.registro.usuarios.domain.model.Phone;
import com.registro.usuarios.domain.model.User;
import java.util.ArrayList;
import java.util.List;

/**
 * Translates between the JSON records and the application types. Manual on purpose: both sides are
 * records, so a forgotten field is a compile error. The request is passed on exactly as received,
 * with absent values left absent, because deciding what is acceptable is the domain's job. The
 * response is built from the aggregate and never reads the password hash.
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

  /** A null list and a null entry stay null: the domain reports them as input errors. */
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
