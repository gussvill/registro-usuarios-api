package com.registro.usuarios.infrastructure.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Un teléfono almacenado. El nombre JSON del código de país se escribe como en el enunciado del
 * ejercicio.
 */
@Schema(description = "Un teléfono almacenado del usuario")
record PhoneResponse(
    @Schema(example = "1234567") String number,
    @Schema(example = "1") String citycode,
    @Schema(example = "57") @JsonProperty("contrycode") String countryCode) {}
