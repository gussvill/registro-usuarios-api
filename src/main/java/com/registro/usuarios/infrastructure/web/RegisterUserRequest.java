package com.registro.usuarios.infrastructure.web;

import com.registro.usuarios.domain.model.Email;
import com.registro.usuarios.domain.model.User;
import com.registro.usuarios.domain.policy.Password;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.List;

/**
 * Cuerpo de {@code POST /api/v1/users}. No lleva anotaciones de validación: todas las reglas viven
 * en el dominio, de modo que son las mismas sin importar quién invoque el caso de uso. Los límites
 * de abajo solo las documentan. Las propiedades que no figuran, como un {@code id} enviado por el
 * cliente, se ignoran.
 */
@Schema(description = "Datos del usuario que se registra")
record RegisterUserRequest(
    @Schema(
            description = "Nombre completo",
            example = "Juan Rodriguez",
            requiredMode = RequiredMode.REQUIRED,
            maxLength = User.NAME_MAX_LENGTH)
        String name,
    @Schema(
            description = "Correo electrónico, almacenado en minúsculas. No debe estar registrado.",
            example = "juan@rodriguez.org",
            requiredMode = RequiredMode.REQUIRED,
            maxLength = Email.MAX_LENGTH)
        String email,
    @Schema(
            description = "Contraseña, de a lo sumo 72 bytes UTF-8. El formato es configurable.",
            example = "hunter2",
            format = "password",
            requiredMode = RequiredMode.REQUIRED,
            maxLength = Password.MAX_BYTES)
        String password,
    @Schema(description = "Teléfonos del usuario, hasta 10. Puede omitirse.")
        List<PhoneRequest> phones) {

  /** La contraseña nunca se imprime, esté presente o no. */
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
