package com.registro.usuarios.infrastructure.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.registro.usuarios.domain.model.Phone;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;

/**
 * A phone as submitted. The JSON name of the country code is spelled as in the exercise statement,
 * on purpose, and differs from the Java name.
 */
@Schema(description = "Un teléfono del usuario")
record PhoneRequest(
    @Schema(
            description = "Número de teléfono, solo dígitos",
            example = "1234567",
            requiredMode = RequiredMode.REQUIRED,
            maxLength = Phone.NUMBER_MAX_LENGTH,
            pattern = Phone.DIGITS_PATTERN)
        String number,
    @Schema(
            description = "Código de ciudad, solo dígitos",
            example = "1",
            requiredMode = RequiredMode.REQUIRED,
            maxLength = Phone.CODE_MAX_LENGTH,
            pattern = Phone.DIGITS_PATTERN)
        String citycode,
    @Schema(
            description = "Código de país, con un + inicial opcional",
            example = "57",
            requiredMode = RequiredMode.REQUIRED,
            maxLength = Phone.CODE_MAX_LENGTH,
            pattern = Phone.COUNTRY_CODE_PATTERN)
        @JsonProperty("contrycode")
        String countryCode) {}
