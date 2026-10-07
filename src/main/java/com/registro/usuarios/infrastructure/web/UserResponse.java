package com.registro.usuarios.infrastructure.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The registered user. It has no component for the password or its hash, so neither can be written
 * by mistake. The JSON names of the last login and of the active flag are those of the exercise
 * statement. The phones are always an array.
 */
@Schema(description = "The registered user and the data generated for it")
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
    @Schema(description = "Generated identifier", example = "0b9e3b0e-6a4c-4d52-9b8e-1f1f2d6d7a10")
        UUID id,
    @Schema(example = "Juan Rodriguez") String name,
    @Schema(description = "Lower-cased email", example = "juan@rodriguez.org") String email,
    List<PhoneResponse> phones,
    @Schema(description = "Creation instant, UTC", example = "2026-01-15T10:30:00Z")
        Instant created,
    @Schema(description = "Last modification instant, UTC", example = "2026-01-15T10:30:00Z")
        Instant modified,
    @Schema(
            description = "Last login instant, UTC; equals the creation instant for a new user",
            example = "2026-01-15T10:30:00Z")
        @JsonProperty("last_login")
        Instant lastLogin,
    @Schema(description = "Signed JWT issued for the user") String token,
    @Schema(description = "Whether the user is active", example = "true") @JsonProperty("isactive")
        boolean active) {

  /** The token is a credential: the web framework prints response objects when it traces. */
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
