package com.registro.usuarios.infrastructure.web;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;

/** La forma única de todo cuerpo de error: un mensaje para el cliente. */
@Schema(description = "Cuerpo de todo error: un único mensaje")
record ErrorResponse(
    @Schema(example = "El correo ya registrado", requiredMode = RequiredMode.REQUIRED)
        String mensaje) {}
