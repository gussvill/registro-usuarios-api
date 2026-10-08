package com.registro.usuarios.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.registro.usuarios.application.port.RegisterUser;
import com.registro.usuarios.application.port.RegisterUserCommand;
import com.registro.usuarios.domain.exception.EmailAlreadyRegisteredException;
import com.registro.usuarios.domain.exception.InvalidUserDataException;
import com.registro.usuarios.domain.model.Reason;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Cada categoría del contrato de errores, verificada en estado, tipo de contenido y cuerpo exacto:
 * un objeto JSON con la única clave {@code mensaje}, en español, UTF-8, sin nada interno. El caso
 * de uso es un mock, así que cada prueba provoca la categoría solo en la capa web.
 */
@ExtendWith(OutputCaptureExtension.class)
@WebMvcTest(UserController.class)
@Import({JacksonConfig.class, GlobalExceptionHandler.class})
class ErrorContractTest {

  private static final String STATEMENT_BODY =
      "{\"name\":\"Juan Rodriguez\",\"email\":\"juan@rodriguez.org\",\"password\":\"hunter2\","
          + "\"phones\":[{\"number\":\"1234567\",\"citycode\":\"1\",\"contrycode\":\"57\"}]}";
  private static final String INVALID_BODY = "El cuerpo de la solicitud no es válido";
  private static final String INTERNAL = "Error interno del servidor";
  private static final List<String> FRAMEWORK_KEYS =
      List.of(
          "timestamp", "status", "error", "path", "message", "trace", "type", "title", "detail");

  @Autowired private MockMvc mvc;
  @MockitoBean private RegisterUser useCase;
  private final JsonMapper json = new JsonMapper();

  // --- utilidades ---

  private MvcResult send(MockHttpServletRequestBuilder request) throws Exception {
    return mvc.perform(request).andReturn();
  }

  private MvcResult register(String body) throws Exception {
    return send(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(body));
  }

  /**
   * Los bytes crudos de la respuesta decodificados como UTF-8, sea cual sea el charset que declare
   * la respuesta.
   */
  private static String text(MvcResult result) throws Exception {
    return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
  }

  /** Estado, tipo de contenido JSON y cuerpo de una sola clave, y luego el mensaje. */
  private void assertError(MvcResult result, int status, String mensaje) throws Exception {
    MockHttpServletResponse response = result.getResponse();
    assertThat(response.getStatus()).as("estado").isEqualTo(status);
    assertThat(response.getContentType()).as("tipo de contenido").isNotNull();
    MediaType type = MediaType.parseMediaType(response.getContentType());
    assertThat(type.getType()).isEqualTo("application");
    assertThat(type.getSubtype()).isEqualTo("json");

    JsonNode body = json.readTree(text(result));
    assertThat(body.isObject()).as("el cuerpo es un objeto: " + text(result)).isTrue();
    assertThat(new ArrayList<>(body.propertyNames())).containsExactly("mensaje");
    assertThat(body.get("mensaje").isString()).isTrue();
    assertThat(body.get("mensaje").asString()).isEqualTo(mensaje);
    for (String key : FRAMEWORK_KEYS) {
      assertThat(body.has(key)).as("clave del framework " + key).isFalse();
    }
  }

  private void rejectWith(Reason... reasons) {
    when(useCase.register(any()))
        .thenThrow(new InvalidUserDataException(EnumSet.copyOf(List.of(reasons))));
  }

  // --- fallos de validación lanzados por el caso de uso ---

  @Test
  void aSingleViolationIsTheExactBody() throws Exception {
    rejectWith(Reason.NAME_REQUIRED);

    MvcResult result = register(STATEMENT_BODY);

    assertError(result, 400, "El nombre es obligatorio");
    assertThat(text(result)).isEqualTo("{\"mensaje\":\"El nombre es obligatorio\"}");
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource({
    "NAME_REQUIRED, El nombre es obligatorio",
    "NAME_TOO_LONG, El nombre no debe superar 255 caracteres",
    "EMAIL_REQUIRED, El correo es obligatorio",
    "EMAIL_TOO_LONG, El correo no debe superar 254 caracteres",
    "EMAIL_FORMAT, El correo no tiene un formato válido",
    "PASSWORD_REQUIRED, La contraseña es obligatoria",
    "PASSWORD_TOO_LONG, La contraseña es demasiado larga",
    "PASSWORD_FORMAT, La contraseña no cumple el formato requerido",
    "PHONES_TOO_MANY, No se permiten más de 10 teléfonos",
    "PHONE_NULL, El teléfono no puede ser nulo",
    "PHONE_NUMBER_REQUIRED, El número de teléfono es obligatorio",
    "PHONE_NUMBER_TOO_LONG, El número de teléfono no debe superar 20 caracteres",
    "PHONE_NUMBER_FORMAT, El número de teléfono solo puede contener dígitos",
    "CITY_CODE_REQUIRED, El código de ciudad es obligatorio",
    "CITY_CODE_TOO_LONG, El código de ciudad no debe superar 10 caracteres",
    "CITY_CODE_FORMAT, El código de ciudad solo puede contener dígitos",
    "COUNTRY_CODE_REQUIRED, El código de país es obligatorio",
    "COUNTRY_CODE_TOO_LONG, El código de país no debe superar 10 caracteres",
    "COUNTRY_CODE_FORMAT, 'El código de país solo puede contener dígitos, con un + inicial opcional'"
  })
  void everyReasonIsAnswered400WithItsCatalogueText(Reason reason, String mensaje)
      throws Exception {
    rejectWith(reason);

    assertError(register(STATEMENT_BODY), 400, mensaje);
  }

  @Test
  void everyReasonHasARowInTheTableAbove() throws Exception {
    CsvSource table =
        getClass()
            .getDeclaredMethod(
                "everyReasonIsAnswered400WithItsCatalogueText", Reason.class, String.class)
            .getAnnotation(CsvSource.class);

    assertThat(Stream.of(table.value()).map(row -> row.substring(0, row.indexOf(',')).strip()))
        .containsExactlyInAnyOrderElementsOf(Stream.of(Reason.values()).map(Enum::name).toList());
  }

  @Test
  void severalViolationsAreJoinedAndSortedAscending() throws Exception {
    rejectWith(Reason.NAME_REQUIRED, Reason.EMAIL_REQUIRED, Reason.PASSWORD_REQUIRED);

    assertError(
        register("{}"),
        400,
        "El correo es obligatorio; El nombre es obligatorio; La contraseña es obligatoria");
  }

  @Test
  void violationsAcrossTheUserAndItsPhonesAreSortedByTheirText() throws Exception {
    rejectWith(
        Reason.PHONE_NUMBER_REQUIRED,
        Reason.NAME_REQUIRED,
        Reason.COUNTRY_CODE_REQUIRED,
        Reason.CITY_CODE_REQUIRED);

    assertError(
        register("{\"name\":\" \",\"phones\":[{}]}"),
        400,
        "El código de ciudad es obligatorio; El código de país es obligatorio; "
            + "El nombre es obligatorio; El número de teléfono es obligatorio");
  }

  @Test
  void aMixedFormatAndRequiredViolationIsJoined() throws Exception {
    rejectWith(Reason.EMAIL_FORMAT, Reason.NAME_REQUIRED);

    assertError(
        register("{\"name\":\"  \",\"email\":\"juan\",\"password\":\"hunter2\"}"),
        400,
        "El correo no tiene un formato válido; El nombre es obligatorio");
  }

  @Test
  void theRejectedValueIsNotEchoedAndNoReasonCodeLeaks() throws Exception {
    rejectWith(Reason.PASSWORD_FORMAT);

    MvcResult result =
        register(
            "{\"name\":\"Juan\",\"email\":\"juan@rodriguez.org\",\"password\":\"abc12\","
                + "\"phones\":[]}");

    assertError(result, 400, "La contraseña no cumple el formato requerido");
    assertThat(text(result)).doesNotContain("abc12").doesNotContain("PASSWORD_FORMAT");
    assertThat(text(result)).doesNotContain("Invalid user data").doesNotContain("juan@");
  }

  // --- correo duplicado ---

  @Test
  void aDuplicateEmailIsTheExact409Body() throws Exception {
    when(useCase.register(any())).thenThrow(new EmailAlreadyRegisteredException());

    MvcResult result = register(STATEMENT_BODY);

    assertError(result, 409, "El correo ya registrado");
    assertThat(text(result)).isEqualTo("{\"mensaje\":\"El correo ya registrado\"}");
  }

  @Test
  void aDuplicateTranslatedFromTheConstraintCarriesNoDatabaseText() throws Exception {
    // El adaptador traduce la violación de la restricción a esta excepción y descarta la causa.
    when(useCase.register(any())).thenThrow(new EmailAlreadyRegisteredException());

    String body = text(register(STATEMENT_BODY));

    assertThat(body)
        .doesNotContain("uk_users_email")
        .doesNotContain("USERS")
        .doesNotContain("constraint")
        .doesNotContain("SQL");
  }

  // --- cuerpos mal formados o con tipos incorrectos ---

  @ParameterizedTest(name = "{0}")
  @MethodSource("bodiesThatAreNotAUsableObject")
  void aBodyThatIsNotAUsableJsonObjectIsTheGenericBodyError(String label, String body)
      throws Exception {
    assertError(register(body), 400, INVALID_BODY);

    verifyNoInteractions(useCase);
  }

  static Stream<Arguments> bodiesThatAreNotAUsableObject() {
    return Stream.of(
        Arguments.of("truncated json", "{\"name\":"),
        Arguments.of("not json at all", "this is not json"),
        Arguments.of("empty body", ""),
        Arguments.of("top-level array", "[]"),
        Arguments.of("top-level scalar", "42"),
        Arguments.of("literal null", "null"),
        Arguments.of("unbalanced braces", "{{{{"),
        Arguments.of("trailing garbage", "{\"name\":\"Juan\"} extra"));
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = {"123", "1.5", "true", "false", "-7", "1e3"})
  void aNonStringScalarForAStringFieldIsTheGenericBodyError(String scalar) throws Exception {
    assertError(register("{\"name\":" + scalar + "}"), 400, INVALID_BODY);

    verifyNoInteractions(useCase);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("wronglyTypedFields")
  void aValueOfTheWrongJsonTypeIsTheGenericBodyError(String label, String body) throws Exception {
    assertError(register(body), 400, INVALID_BODY);

    verifyNoInteractions(useCase);
  }

  static Stream<Arguments> wronglyTypedFields() {
    return Stream.of(
        Arguments.of("name is an object", "{\"name\":{}}"),
        Arguments.of("name is an array", "{\"name\":[\"Juan\"]}"),
        Arguments.of("email is an array", "{\"email\":[\"a@dominio.cl\"]}"),
        Arguments.of("email is a number", "{\"email\":12}"),
        Arguments.of("password is a number", "{\"password\":1234567}"),
        Arguments.of("password is a boolean", "{\"password\":true}"),
        Arguments.of("phones is a string", "{\"phones\":\"x\"}"),
        Arguments.of("phones is an object", "{\"phones\":{}}"),
        Arguments.of("a phone is a number", "{\"phones\":[5]}"),
        Arguments.of("a phone is a string", "{\"phones\":[\"x\"]}"),
        Arguments.of("number is a number", "{\"phones\":[{\"number\":1234567}]}"),
        Arguments.of("citycode is a number", "{\"phones\":[{\"citycode\":1}]}"),
        Arguments.of("contrycode is a boolean", "{\"phones\":[{\"contrycode\":false}]}"));
  }

  @Test
  void aWrongTypeBeatsTheValidationOfTheOtherFields() throws Exception {
    // Falta también "email", pero solo se devuelve el mensaje del cuerpo.
    rejectWith(Reason.EMAIL_REQUIRED);

    MvcResult result = register("{\"name\":123}");

    assertError(result, 400, INVALID_BODY);
    verifyNoInteractions(useCase);
  }

  @Test
  void theParserTextNeverReachesTheClient() throws Exception {
    for (String body : List.of("{\"name\":", "{\"name\":123}", "{{", "[[[[", "")) {
      String answer = text(register(body));

      assertThat(answer)
          .doesNotContain("Unexpected")
          .doesNotContain("line:")
          .doesNotContain("column:")
          .doesNotContain("tools.jackson")
          .doesNotContain("com.fasterxml")
          .doesNotContain("Exception")
          .doesNotContain("MismatchedInput")
          .doesNotContain("RegisterUserRequest");
    }
  }

  @Test
  void quotedDigitsAreStillAValidStringAndReachTheUseCase() throws Exception {
    rejectWith(Reason.NAME_REQUIRED);

    register("{\"name\":\"123\",\"phones\":[{\"number\":\"1234567\",\"citycode\":\"1\"}]}");

    ArgumentCaptor<RegisterUserCommand> command =
        ArgumentCaptor.forClass(RegisterUserCommand.class);
    verify(useCase).register(command.capture());
    assertThat(command.getValue().name()).isEqualTo("123");
    assertThat(command.getValue().phones().get(0).number()).isEqualTo("1234567");
  }

  @Test
  void aNullStringFieldStillReachesTheUseCaseAsMissing() throws Exception {
    rejectWith(Reason.NAME_REQUIRED);

    assertError(register("{\"name\":null}"), 400, "El nombre es obligatorio");

    ArgumentCaptor<RegisterUserCommand> command =
        ArgumentCaptor.forClass(RegisterUserCommand.class);
    verify(useCase).register(command.capture());
    assertThat(command.getValue().name()).isNull();
  }

  // --- enrutamiento, método, tipos de medio ---

  @Test
  void anUnknownPathIs404() throws Exception {
    MvcResult result = send(get("/api/v1/does-not-exist"));

    assertError(result, 404, "Recurso no encontrado");
    assertThat(text(result)).isEqualTo("{\"mensaje\":\"Recurso no encontrado\"}");
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = {"/", "/nope", "/api", "/api/v1", "/api/v1/users/42"})
  void otherUnknownPathsAre404(String path) throws Exception {
    assertError(send(get(path)), 404, "Recurso no encontrado");
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = {"GET", "PUT", "DELETE"})
  void aWrongMethodOnTheUsersRouteIs405WithAnAllowHeaderListingPost(String method)
      throws Exception {
    MockHttpServletRequestBuilder request =
        switch (method) {
          case "GET" -> get("/api/v1/users");
          case "PUT" -> put("/api/v1/users");
          default -> delete("/api/v1/users");
        };

    MvcResult result = send(request);

    assertError(result, 405, "Método no permitido");
    assertThat(result.getResponse().getHeader("Allow")).contains("POST");
  }

  @ParameterizedTest(name = "Accept: {0}")
  @ValueSource(strings = {"application/xml", "text/html", "application/xml, text/html"})
  void anAcceptThatExcludesJsonIs406AndTheBodyIsStillJson(String accept) throws Exception {
    MvcResult result =
        send(
            post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(accept)
                .content(STATEMENT_BODY));

    assertError(result, 406, "Formato de respuesta no aceptable");
    assertThat(text(result)).isEqualTo("{\"mensaje\":\"Formato de respuesta no aceptable\"}");
    verifyNoInteractions(useCase);
  }

  @ParameterizedTest(name = "Content-Type: {0}")
  @ValueSource(strings = {"text/plain", "application/xml", "application/x-www-form-urlencoded"})
  void aContentTypeThatIsNotJsonIs415(String contentType) throws Exception {
    MvcResult result = send(post("/api/v1/users").contentType(contentType).content(STATEMENT_BODY));

    assertError(result, 415, "Tipo de contenido no soportado");
    verifyNoInteractions(useCase);
  }

  @Test
  void aMissingContentTypeIs415() throws Exception {
    MvcResult result = send(post("/api/v1/users").content(STATEMENT_BODY));

    assertError(result, 415, "Tipo de contenido no soportado");
  }

  // --- fallos inesperados ---

  @Test
  void anUnexpectedFailureIsAGenericBodyWithNothingInternal() throws Exception {
    when(useCase.register(any())).thenThrow(new RuntimeException("secret-internal-detail"));

    MvcResult result = register(STATEMENT_BODY);

    assertError(result, 500, INTERNAL);
    assertThat(text(result)).isEqualTo("{\"mensaje\":\"Error interno del servidor\"}");
    assertThat(text(result))
        .doesNotContain("secret-internal-detail")
        .doesNotContain("RuntimeException")
        .doesNotContain("java.")
        .doesNotContain("com.registro")
        .doesNotContain("at ");
  }

  @Test
  void aDatabaseFailureThatIsNotADuplicateIsTheSameGenericBody() throws Exception {
    when(useCase.register(any()))
        .thenThrow(
            new DataIntegrityViolationException(
                "could not execute statement [NULL not allowed for column \"NAME\"]; "
                    + "SQL [insert into users ...]; constraint [uk_users_email]"));

    MvcResult result = register(STATEMENT_BODY);

    assertError(result, 500, INTERNAL);
    assertThat(text(result))
        .doesNotContain("insert")
        .doesNotContain("uk_users_email")
        .doesNotContain("NAME")
        .doesNotContain("DataIntegrity");
  }

  @Test
  void aFailureWithNoMessageIsStillTheGenericBody() throws Exception {
    when(useCase.register(any())).thenThrow(new IllegalStateException());

    assertError(register(STATEMENT_BODY), 500, INTERNAL);
  }

  @Test
  void aServerErrorOfAStandardMvcExceptionIsLoggedWithTheClassAndNoMessage(CapturedOutput output)
      throws Exception {
    when(useCase.register(any()))
        .thenThrow(new HttpMessageNotWritableException("cannot write secret-mvc-value"));

    MvcResult result = register(STATEMENT_BODY);

    assertError(result, 500, INTERNAL);
    assertThat(output.getAll())
        .contains("ERROR")
        .contains(
            "Unexpected failure while handling a request: "
                + "org.springframework.http.converter.HttpMessageNotWritableException")
        .doesNotContain("secret-mvc-value");
  }

  @Test
  void aClientErrorOfAStandardMvcExceptionIsNotLoggedAsAnUnexpectedFailure(CapturedOutput output)
      throws Exception {
    MvcResult result = register("{\"name\":");

    assertError(result, 400, INVALID_BODY);
    assertThat(output.getAll()).doesNotContain("Unexpected failure");
  }

  // --- codificación ---

  @Test
  void accentedMessagesAreUtf8BytesNotEscapesOrReplacementCharacters() throws Exception {
    rejectWith(Reason.PASSWORD_REQUIRED);

    MvcResult result = register("{\"name\":\"Juan\",\"email\":\"juan@rodriguez.org\"}");

    byte[] raw = result.getResponse().getContentAsByteArray();
    byte[] expected =
        "{\"mensaje\":\"La contraseña es obligatoria\"}".getBytes(StandardCharsets.UTF_8);
    assertThat(raw).isEqualTo(expected);
    // "n con tilde" son los dos bytes C3 B1; sin escape \\u00f1 ni U+FFFD (EF BF BD).
    assertThat(new String(raw, StandardCharsets.ISO_8859_1)).contains("Ã±");
    assertThat(new String(raw, StandardCharsets.UTF_8))
        .doesNotContain("\\u00f1")
        .doesNotContain("�");
    assertError(result, 400, "La contraseña es obligatoria");
  }

  @Test
  void accentsSurviveOnTheFrameworkErrorsToo() throws Exception {
    MvcResult result = send(get("/api/v1/users"));

    assertThat(result.getResponse().getContentAsByteArray())
        .isEqualTo("{\"mensaje\":\"Método no permitido\"}".getBytes(StandardCharsets.UTF_8));
  }
}
