package com.registro.usuarios.infrastructure.web;

import io.swagger.v3.oas.annotations.media.Schema;

/** The single shape of every error body: one message for the client. */
@Schema(description = "Error body: a single message")
record ErrorResponse(@Schema(example = "El correo ya registrado") String mensaje) {}
