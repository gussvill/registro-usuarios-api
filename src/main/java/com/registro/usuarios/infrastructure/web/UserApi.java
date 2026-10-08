package com.registro.usuarios.infrastructure.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * El contrato HTTP del registro de usuarios: el mapeo y la descripción OpenAPI viven aquí, para que
 * el controlador solo tenga que traducir y delegar. Lo implementa {@code UserController}.
 */
@Tag(name = "Usuarios", description = "Registro de usuarios")
interface UserApi {

  String STATEMENT_EXAMPLE =
      "{\"name\":\"Juan Rodriguez\",\"email\":\"juan@rodriguez.org\",\"password\":\"hunter2\","
          + "\"phones\":[{\"number\":\"1234567\",\"citycode\":\"1\",\"contrycode\":\"57\"}]}";

  @Operation(
      summary = "Registrar un usuario",
      description =
          "Registra un usuario con sus teléfonos y responde con los datos almacenados, un"
              + " identificador generado, los instantes de creación y un JWT firmado. La"
              + " contraseña se guarda solo como hash BCrypt con sal y nunca se devuelve. La"
              + " respuesta 201 lleva el encabezado Cache-Control: no-store porque contiene un"
              + " token al portador. Todo error responde un objeto JSON con un único campo"
              + " \"mensaje\".",
      requestBody =
          @io.swagger.v3.oas.annotations.parameters.RequestBody(
              required = true,
              content =
                  @Content(
                      mediaType = MediaType.APPLICATION_JSON_VALUE,
                      schema = @Schema(implementation = RegisterUserRequest.class),
                      examples =
                          @ExampleObject(
                              name = "Ejemplo del enunciado",
                              value = STATEMENT_EXAMPLE))))
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "Usuario registrado",
        headers =
            @Header(
                name = "Cache-Control",
                description =
                    "Siempre no-store: la respuesta lleva un token al portador y no debe"
                        + " almacenarse en ninguna caché",
                schema = @Schema(type = "string", example = "no-store")),
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = UserResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "El cuerpo no es válido o uno o más campos incumplen una regla",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = "La ruta no existe, por ejemplo con una barra final: /api/v1/users/",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ErrorResponse.class),
                examples =
                    @ExampleObject(
                        name = "Ruta desconocida",
                        value = "{\"mensaje\":\"Recurso no encontrado\"}"))),
    @ApiResponse(
        responseCode = "405",
        description =
            "El método HTTP no está permitido en esta ruta; la respuesta incluye el encabezado Allow",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ErrorResponse.class),
                examples =
                    @ExampleObject(
                        name = "Método no permitido",
                        value = "{\"mensaje\":\"Método no permitido\"}"))),
    @ApiResponse(
        responseCode = "406",
        description = "El encabezado Accept no admite application/json",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ErrorResponse.class),
                examples =
                    @ExampleObject(
                        name = "Formato no aceptable",
                        value = "{\"mensaje\":\"Formato de respuesta no aceptable\"}"))),
    @ApiResponse(
        responseCode = "409",
        description = "El correo ya está registrado",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ErrorResponse.class),
                examples =
                    @ExampleObject(
                        name = "Correo duplicado",
                        value = "{\"mensaje\":\"El correo ya registrado\"}"))),
    @ApiResponse(
        responseCode = "415",
        description = "El cuerpo de la solicitud no es application/json",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "500",
        description = "Falla inesperada; el cuerpo nunca incluye detalles internos",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ErrorResponse.class)))
  })
  @PostMapping(
      path = "/api/v1/users",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<UserResponse> register(@RequestBody RegisterUserRequest request);
}
