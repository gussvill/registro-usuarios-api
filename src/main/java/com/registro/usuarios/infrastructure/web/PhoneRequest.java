package com.registro.usuarios.infrastructure.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.registro.usuarios.domain.model.Phone;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;

/**
 * A phone as submitted. The JSON name of the country code is spelled as in the exercise statement,
 * on purpose, and differs from the Java name.
 */
@Schema(description = "A phone of the user")
record PhoneRequest(
    @Schema(
            description = "Phone number, digits only",
            example = "1234567",
            requiredMode = RequiredMode.REQUIRED,
            maxLength = Phone.NUMBER_MAX_LENGTH,
            pattern = Phone.DIGITS_PATTERN)
        String number,
    @Schema(
            description = "City code, digits only",
            example = "1",
            requiredMode = RequiredMode.REQUIRED,
            maxLength = Phone.CODE_MAX_LENGTH,
            pattern = Phone.DIGITS_PATTERN)
        String citycode,
    @Schema(
            description = "Country code, with an optional leading +",
            example = "57",
            requiredMode = RequiredMode.REQUIRED,
            maxLength = Phone.CODE_MAX_LENGTH,
            pattern = Phone.COUNTRY_CODE_PATTERN)
        @JsonProperty("contrycode")
        String countryCode) {}
