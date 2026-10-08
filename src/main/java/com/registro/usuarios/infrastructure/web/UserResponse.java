package com.registro.usuarios.infrastructure.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * El usuario registrado. No tiene componente para la contraseña ni su hash, de modo que ninguno
 * puede escribirse por error. Los nombres JSON del último acceso y del indicador de activo son los
 * del enunciado del ejercicio. Los teléfonos son siempre un arreglo.
 */
@Schema(description = "El usuario registrado y los datos generados para él")
@JsonPropertyOrder({
  "id",
  "name",
  "email",
  "phones",
  "created",
  "modified",
  "last_login",
  "token",
  "isactive"
})
record UserResponse(
    @Schema(
            description = "Identificador generado",
            example = "0b9e3b0e-6a4c-4d52-9b8e-1f1f2d6d7a10",
            requiredMode = RequiredMode.REQUIRED)
        UUID id,
    @Schema(example = "Juan Rodriguez", requiredMode = RequiredMode.REQUIRED) String name,
    @Schema(
            description = "Correo en minúsculas",
            example = "juan@rodriguez.org",
            requiredMode = RequiredMode.REQUIRED)
        String email,
    @Schema(requiredMode = RequiredMode.REQUIRED) List<PhoneResponse> phones,
    @Schema(
            description = "Instante de creación, UTC",
            example = "2026-01-15T10:30:00Z",
            requiredMode = RequiredMode.REQUIRED)
        Instant created,
    @Schema(
            description = "Instante de la última modificación, UTC",
            example = "2026-01-15T10:30:00Z",
            requiredMode = RequiredMode.REQUIRED)
        Instant modified,
    @Schema(
            description =
                "Instante del último acceso, UTC; igual al de creación en un usuario nuevo",
            example = "2026-01-15T10:30:00Z",
            requiredMode = RequiredMode.REQUIRED)
        @JsonProperty("last_login")
        Instant lastLogin,
    @Schema(
            description = "JWT firmado emitido para el usuario",
            requiredMode = RequiredMode.REQUIRED)
        String token,
    @Schema(
            description = "Indica si el usuario está activo",
            example = "true",
            requiredMode = RequiredMode.REQUIRED)
        @JsonProperty("isactive")
        boolean active) {

  /**
   * El token es una credencial: el framework web imprime los objetos de respuesta cuando hace
   * trazas.
   */
  @Override
  public String toString() {
    return "UserResponse[id="
        + id
        + ", name="
        + name
        + ", email="
        + email
        + ", phones="
        + phones
        + ", created="
        + created
        + ", modified="
        + modified
        + ", lastLogin="
        + lastLogin
        + ", token=<redacted>, active="
        + active
        + "]";
  }
}
