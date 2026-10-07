package com.registro.usuarios.infrastructure.web;

import com.registro.usuarios.domain.model.Email;
import com.registro.usuarios.domain.model.User;
import com.registro.usuarios.domain.policy.PasswordPolicy;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.List;

/**
 * Body of {@code POST /api/v1/users}. It carries no validation annotation: every rule lives in the
 * domain, so that it is the same whoever calls the use case. The limits below only document them.
 * Properties that are not listed, such as a client-supplied {@code id}, are ignored.
 */
@Schema(description = "Data of the user to register")
record RegisterUserRequest(
    @Schema(
            description = "Full name",
            example = "Juan Rodriguez",
            requiredMode = RequiredMode.REQUIRED,
            maxLength = User.NAME_MAX_LENGTH)
        String name,
    @Schema(
            description = "Email address, stored in lower case. Must be unused.",
            example = "juan@rodriguez.org",
            requiredMode = RequiredMode.REQUIRED,
            maxLength = Email.MAX_LENGTH)
        String email,
    @Schema(
            description = "Password, at most 72 UTF-8 bytes. The format is configurable.",
            example = "hunter2",
            format = "password",
            requiredMode = RequiredMode.REQUIRED,
            maxLength = PasswordPolicy.MAX_BYTES)
        String password,
    @Schema(description = "Phones of the user, at most 10. May be absent.")
        List<PhoneRequest> phones) {

  /** The password is never printed, whether or not it is present. */
  @Override
  public String toString() {
    return "RegisterUserRequest[name="
        + name
        + ", email="
        + email
        + ", password=<redacted>, phones="
        + phones
        + "]";
  }
}
