package com.registro.usuarios.infrastructure.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;

/**
 * Un teléfono almacenado. El nombre JSON del código de país se escribe como en el enunciado del
 * ejercicio.
 */
@Schema(description = "Un teléfono almacenado del usuario")
record PhoneResponse(
    @Schema(example = "1234567", requiredMode = RequiredMode.REQUIRED) String number,
    @Schema(example = "1", requiredMode = RequiredMode.REQUIRED) String citycode,
    @Schema(example = "57", requiredMode = RequiredMode.REQUIRED) @JsonProperty("contrycode")
        String countryCode) {}
