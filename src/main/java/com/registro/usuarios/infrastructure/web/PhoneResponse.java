package com.registro.usuarios.infrastructure.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/** A stored phone. The JSON name of the country code is spelled as in the exercise statement. */
@Schema(description = "Un teléfono almacenado del usuario")
record PhoneResponse(
    @Schema(example = "1234567") String number,
    @Schema(example = "1") String citycode,
    @Schema(example = "57") @JsonProperty("contrycode") String countryCode) {}
