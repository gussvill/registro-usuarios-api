package com.registro.usuarios.infrastructure.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * The HTTP contract of user registration: the mapping and the OpenAPI description live here, so
 * that the controller only has to translate and delegate.
 */
@Tag(name = "Users", description = "Registration of users")
interface UserApi {

  String STATEMENT_EXAMPLE =
      "{\"name\":\"Juan Rodriguez\",\"email\":\"juan@rodriguez.org\",\"password\":\"hunter2\","
          + "\"phones\":[{\"number\":\"1234567\",\"citycode\":\"1\",\"contrycode\":\"57\"}]}";

  @Operation(
      summary = "Register a user",
      description =
          "Registers a user with its phones and answers with the stored data, a generated"
              + " identifier, the creation instants and a signed JWT. The password is stored only"
              + " as a salted BCrypt hash and is never returned. Every error answers a JSON object"
              + " with a single \"mensaje\" field.",
      requestBody =
          @io.swagger.v3.oas.annotations.parameters.RequestBody(
              required = true,
              content =
                  @Content(
                      mediaType = MediaType.APPLICATION_JSON_VALUE,
                      schema = @Schema(implementation = RegisterUserRequest.class),
                      examples =
                          @ExampleObject(name = "Statement example", value = STATEMENT_EXAMPLE))))
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "User registered",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = UserResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "The body is not valid or one or more fields break a rule",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "409",
        description = "The email is already registered",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ErrorResponse.class),
                examples =
                    @ExampleObject(
                        name = "Duplicate email",
                        value = "{\"mensaje\":\"El correo ya registrado\"}"))),
    @ApiResponse(
        responseCode = "415",
        description = "The request body is not application/json",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "500",
        description = "Unexpected failure; the body never carries internal details",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ErrorResponse.class)))
  })
  @PostMapping(
      path = "/api/v1/users",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  UserResponse register(@RequestBody RegisterUserRequest request);
}
