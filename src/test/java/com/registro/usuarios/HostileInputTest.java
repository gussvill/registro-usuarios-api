package com.registro.usuarios;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeout;

import com.registro.usuarios.support.FullContextTest;
import com.registro.usuarios.support.RegistrationClient;
import com.registro.usuarios.support.RegistrationClient.Reply;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

/**
 * Entradas hostiles y de borde contra la aplicación en ejecución, con la base de datos real. Toda
 * petición debe responderse en menos de cinco segundos y nunca ser un error de servidor: un valor
 * incorrecto es un 400 con su mensaje exacto. Las pruebas usan su propio correo o eliminan primero
 * el fijo, de modo que no dependen de las demás pruebas de contexto completo.
 */
@FullContextTest
class HostileInputTest {

  private static final Duration BOUND = Duration.ofSeconds(5);
  private static final String INVALID_BODY = "El cuerpo de la solicitud no es válido";
  private static final String EMAIL_FORMAT = "El correo no tiene un formato válido";

  @Autowired private JdbcTemplate jdbc;

  @Value("${local.server.port}")
  private int port;

  private RegistrationClient api;

  @BeforeEach
  void connect() {
    api = new RegistrationClient(port);
  }

  // --- utilidades ---

  private static String quote(String text) {
    return "\"" + text + "\"";
  }

  /**
   * Un cuerpo de registro como texto. Cada override es un nombre de campo y su JSON en bruto; un
   * valor en bruto {@code null} elimina el campo. El correo es único salvo que se sobrescriba, de
   * modo que un cuerpo que inesperadamente tenga éxito nunca colisione con otro.
   */
  private static String body(String... overrides) {
    Map<String, String> fields = new LinkedHashMap<>();
    fields.put("name", quote("Juan Rodriguez"));
    fields.put("email", quote(RegistrationClient.uniqueEmail()));
    fields.put("password", quote("hunter2"));
    fields.put("phones", "[" + validPhone() + "]");
    for (int i = 0; i < overrides.length; i += 2) {
      fields.put(overrides[i], overrides[i + 1]);
    }
    return fields.entrySet().stream()
        .filter(entry -> entry.getValue() != null)
        .map(entry -> quote(entry.getKey()) + ":" + entry.getValue())
        .collect(Collectors.joining(",", "{", "}"));
  }

  private static String phone(String number, String citycode, String contrycode) {
    List<String> parts = new ArrayList<>();
    if (number != null) {
      parts.add("\"number\":" + number);
    }
    if (citycode != null) {
      parts.add("\"citycode\":" + citycode);
    }
    if (contrycode != null) {
      parts.add("\"contrycode\":" + contrycode);
    }
    return parts.stream().collect(Collectors.joining(",", "{", "}"));
  }

  private static String validPhone() {
    return phone("\"1234567\"", "\"1\"", "\"57\"");
  }

  /** Envía el cuerpo y falla si la respuesta tarda más que el límite. */
  private Reply timed(String requestBody) {
    return assertTimeout(BOUND, () -> api.post(requestBody));
  }

  private void assertRejected(String requestBody, String mensaje) {
    Reply reply = timed(requestBody);

    assertThat(reply.status()).as("estado para %s", mensaje).isEqualTo(400);
    assertThat(reply.contentType()).startsWith("application/json");
    JsonNode answer = reply.json();
    assertThat(new ArrayList<>(answer.propertyNames())).containsExactly("mensaje");
    assertThat(answer.get("mensaje").asString()).isEqualTo(mensaje);
  }

  // --- cada fila hostil con su mensaje exacto ---

  @ParameterizedTest(name = "{0}")
  @MethodSource("hostileRows")
  void everyHostileValueIsA400WithItsExactMessageWithinFiveSeconds(
      String label, String requestBody, String mensaje) {
    assertRejected(requestBody, mensaje);
  }

  static Stream<Arguments> hostileRows() {
    String tooLongName = "El nombre no debe superar 255 caracteres";
    String tooLongEmail = "El correo no debe superar 254 caracteres";
    String tooLongPassword = "La contraseña es demasiado larga";
    return Stream.of(
        // nombre
        Arguments.of(
            "name: 10,000 characters", body("name", quote("a".repeat(10_000))), tooLongName),
        Arguments.of("name: number", body("name", "12345"), INVALID_BODY),
        Arguments.of("name: object", body("name", "{}"), INVALID_BODY),
        Arguments.of("name: null", body("name", "null"), "El nombre es obligatorio"),
        // correo
        Arguments.of(
            "email: 50,000 characters",
            body("email", quote("a".repeat(50_000) + "@x")),
            tooLongEmail),
        Arguments.of("email: array", body("email", "[\"a@dominio.cl\"]"), INVALID_BODY),
        Arguments.of("email: null", body("email", "null"), "El correo es obligatorio"),
        // contraseña
        Arguments.of(
            "password: 100,000 characters",
            body("password", quote("a".repeat(100_000))),
            tooLongPassword),
        Arguments.of("password: number", body("password", "1234567"), INVALID_BODY),
        Arguments.of("password: boolean", body("password", "true"), INVALID_BODY),
        Arguments.of("password: null", body("password", "null"), "La contraseña es obligatoria"),
        // teléfonos
        Arguments.of("phones: string", body("phones", quote("x")), INVALID_BODY),
        Arguments.of("phones: object", body("phones", "{}"), INVALID_BODY),
        Arguments.of("phones: [null]", body("phones", "[null]"), "El teléfono no puede ser nulo"),
        Arguments.of(
            "phones: 11 valid entries",
            body(
                "phones",
                "[" + String.join(",", java.util.Collections.nCopies(11, validPhone())) + "]"),
            "No se permiten más de 10 teléfonos"),
        Arguments.of("phones: [5]", body("phones", "[5]"), INVALID_BODY),
        // campos del teléfono
        Arguments.of(
            "number: 5,000 characters",
            body("phones", "[" + phone(quote("1".repeat(5_000)), "\"1\"", "\"57\"") + "]"),
            "El número de teléfono no debe superar 20 caracteres"),
        Arguments.of(
            "number: integer",
            body("phones", "[" + phone("1234567", "\"1\"", "\"57\"") + "]"),
            INVALID_BODY),
        Arguments.of(
            "citycode: 11 characters",
            body("phones", "[" + phone("\"1234567\"", quote("1".repeat(11)), "\"57\"") + "]"),
            "El código de ciudad no debe superar 10 caracteres"),
        Arguments.of(
            "contrycode: 11 characters",
            body("phones", "[" + phone("\"1234567\"", "\"1\"", quote("5".repeat(11))) + "]"),
            "El código de país no debe superar 10 caracteres"),
        // forma del cuerpo
        Arguments.of("shape: empty array", "[]", INVALID_BODY),
        Arguments.of("shape: literal null", "null", INVALID_BODY),
        Arguments.of("shape: 10,000 nested arrays", "[".repeat(10_000), INVALID_BODY),
        Arguments.of(
            "shape: 10,000 nested arrays closed",
            "[".repeat(10_000) + "]".repeat(10_000),
            INVALID_BODY),
        Arguments.of(
            "shape: 10,000 nested arrays inside a field",
            "{\"name\":" + "[".repeat(10_000) + "]".repeat(10_000) + "}",
            INVALID_BODY),
        Arguments.of(
            "shape: 10,000 nested objects inside a field",
            "{\"name\":" + "{\"a\":".repeat(10_000) + "1" + "}".repeat(10_000) + "}",
            INVALID_BODY));
  }

  // --- límites de la contraseña con el patrón por defecto ---

  @Test
  void aPasswordOf72CharactersIsAccepted() {
    String password = "a".repeat(71) + "1";
    assertThat(password).hasSize(72);

    Reply reply = timed(body("password", quote(password)));

    assertThat(reply.status()).isEqualTo(201);
  }

  @Test
  void aPasswordOf73CharactersIsTooLong() {
    assertRejected(
        body("password", quote("a".repeat(72) + "1")), "La contraseña es demasiado larga");
  }

  @Test
  void fortyAccentedLettersAreEightyBytesAndTooLong() {
    assertRejected(body("password", quote("é".repeat(40))), "La contraseña es demasiado larga");
  }

  @Test
  void aShortPasswordBreaksTheDefaultPattern() {
    assertRejected(
        body("password", quote("abc12")), "La contraseña no cumple el formato requerido");
  }

  // --- campos obligatorios ---

  @ParameterizedTest(name = "{0}")
  @MethodSource("requiredRows")
  void aMissingOrBlankRequiredFieldIsReportedAlone(
      String label, String requestBody, String mensaje) {
    assertRejected(requestBody, mensaje);
  }

  static Stream<Arguments> requiredRows() {
    return Stream.of(
        Arguments.of("name omitted", body("name", null), "El nombre es obligatorio"),
        Arguments.of("email omitted", body("email", null), "El correo es obligatorio"),
        Arguments.of("password omitted", body("password", null), "La contraseña es obligatoria"),
        Arguments.of("name blank", body("name", quote("   ")), "El nombre es obligatorio"),
        Arguments.of("email empty", body("email", quote("")), "El correo es obligatorio"),
        Arguments.of(
            "password blank", body("password", quote("       ")), "La contraseña es obligatoria"),
        Arguments.of(
            "everything missing",
            "{}",
            "El correo es obligatorio; El nombre es obligatorio; La contraseña es obligatoria"),
        Arguments.of(
            "blank name and malformed email",
            body("name", quote("  "), "email", quote("juan")),
            "El correo no tiene un formato válido; El nombre es obligatorio"));
  }

  // --- teléfonos ---

  @ParameterizedTest(name = "{0}")
  @MethodSource("phoneRows")
  void aPhoneWithMissingPartsIsReportedWithTheMatchingMessages(
      String label, String requestBody, String mensaje) {
    assertRejected(requestBody, mensaje);
  }

  static Stream<Arguments> phoneRows() {
    return Stream.of(
        Arguments.of(
            "an empty phone object",
            body("phones", "[{}]"),
            "El código de ciudad es obligatorio; El código de país es obligatorio; "
                + "El número de teléfono es obligatorio"),
        Arguments.of(
            "only contrycode missing",
            body("phones", "[" + phone("\"1234567\"", "\"1\"", null) + "]"),
            "El código de país es obligatorio"),
        Arguments.of(
            "only citycode missing",
            body("phones", "[" + phone("\"1234567\"", null, "\"57\"") + "]"),
            "El código de ciudad es obligatorio"),
        Arguments.of(
            "only number missing",
            body("phones", "[" + phone(null, "\"1\"", "\"57\"") + "]"),
            "El número de teléfono es obligatorio"),
        Arguments.of(
            "two phones both missing the number",
            body(
                "phones",
                "[" + phone(null, "\"1\"", "\"57\"") + "," + phone(null, "\"2\"", "\"56\"") + "]"),
            "El número de teléfono es obligatorio"),
        Arguments.of(
            "a blank code",
            body("phones", "[" + phone("\"1234567\"", quote(" "), "\"57\"") + "]"),
            "El código de ciudad es obligatorio"),
        Arguments.of(
            "a number with a hyphen",
            body("phones", "[" + phone(quote("123-4567"), "\"1\"", "\"57\"") + "]"),
            "El número de teléfono solo puede contener dígitos"),
        Arguments.of(
            "a number with a leading plus",
            body("phones", "[" + phone(quote("+1234567"), "\"1\"", "\"57\"") + "]"),
            "El número de teléfono solo puede contener dígitos"),
        Arguments.of(
            "a city code with letters",
            body("phones", "[" + phone("\"1234567\"", quote("1a"), "\"57\"") + "]"),
            "El código de ciudad solo puede contener dígitos"),
        Arguments.of(
            "a country code with a trailing plus",
            body("phones", "[" + phone("\"1234567\"", "\"1\"", quote("57+")) + "]"),
            "El código de país solo puede contener dígitos, con un + inicial opcional"),
        Arguments.of(
            "a country code with two plus signs",
            body("phones", "[" + phone("\"1234567\"", "\"1\"", quote("++57")) + "]"),
            "El código de país solo puede contener dígitos, con un + inicial opcional"),
        Arguments.of(
            "all three fields with non-digits",
            body("phones", "[" + phone(quote("12 34"), quote("x"), quote("+")) + "]"),
            "El código de ciudad solo puede contener dígitos; "
                + "El código de país solo puede contener dígitos, con un + inicial opcional; "
                + "El número de teléfono solo puede contener dígitos"));
  }

  @Test
  void aPhoneWithALeadingPlusOnTheCountryCodeIsAccepted() {
    Reply reply = timed(body("phones", "[" + phone("\"1234567\"", "\"1\"", quote("+57")) + "]"));

    assertThat(reply.status()).isEqualTo(201);
    assertThat(reply.json().get("phones").get(0).get("contrycode").asString()).isEqualTo("+57");
  }

  @ParameterizedTest(name = "phones: {0}")
  @ValueSource(strings = {"absent", "null", "[]"})
  void absentNullOrEmptyPhonesAreAcceptedAndReturnedAsAnEmptyArray(String shape) {
    String requestBody =
        switch (shape) {
          case "absent" -> body("phones", null);
          case "null" -> body("phones", "null");
          default -> body("phones", "[]");
        };

    Reply reply = timed(requestBody);

    assertThat(reply.status()).isEqualTo(201);
    assertThat(reply.json().get("phones").isArray()).isTrue();
    assertThat(reply.json().get("phones")).isEmpty();
  }

  // --- correo ---

  @ParameterizedTest(name = "[{0}]")
  @ValueSource(
      strings = {
        "juan",
        "juan@",
        "@dominio.cl",
        "juan@dominio",
        "juan@@dominio.cl",
        " juan@dominio.cl",
        "juan@rodriguez.org\n",
        "juan@rodriguez.org ",
        "juan@dominio.c",
        "ju an@dominio.cl",
        "juan@dominio..cl"
      })
  void anInvalidEmailIsRejectedWithTheFormatMessage(String email) {
    String requestBody = body("email", RegistrationClient.JSON.valueToTree(email).toString());

    assertRejected(requestBody, EMAIL_FORMAT);
  }

  @ParameterizedTest(name = "[{0}]")
  @ValueSource(strings = {"aaaaaaa@dominio.cl", "juan@rodriguez.org", "a.b+c@sub.dominio.co.uk"})
  void aValidEmailIsAcceptedAndReturnedLowerCased(String email) {
    RegistrationClient.forget(jdbc, email);

    Reply reply = timed(body("email", quote(email)));

    assertThat(reply.status()).isEqualTo(201);
    assertThat(reply.json().get("email").asString()).isEqualTo(email);
  }

  // --- los valores de longitud máxima se aceptan y se guardan sin truncar ---

  @Test
  void valuesAtTheirMaximumLengthAreAcceptedAndStoredWithoutTruncation() {
    String name = "n".repeat(255);
    String email =
        UUID.randomUUID() + "a".repeat(254 - 36 - "@dominio.cl".length()) + "@dominio.cl";
    assertThat(email).hasSize(254);
    String number = "1".repeat(20);
    String citycode = "2".repeat(10);
    String contrycode = "3".repeat(10);

    Reply reply =
        timed(
            body(
                "name",
                quote(name),
                "email",
                quote(email),
                "phones",
                "[" + phone(quote(number), quote(citycode), quote(contrycode)) + "]"));

    assertThat(reply.status()).isEqualTo(201);
    JsonNode answer = reply.json();
    assertThat(answer.get("name").asString()).isEqualTo(name);
    assertThat(answer.get("email").asString()).isEqualTo(email);
    UUID id = UUID.fromString(answer.get("id").asString());
    assertThat(jdbc.queryForObject("SELECT name FROM users WHERE id = ?", String.class, id))
        .isEqualTo(name);
    assertThat(jdbc.queryForObject("SELECT email FROM users WHERE id = ?", String.class, id))
        .isEqualTo(email);
    Map<String, Object> stored =
        jdbc.queryForMap(
            "SELECT phone_number, city_code, country_code FROM phones WHERE user_id = ?", id);
    assertThat(stored)
        .containsEntry("PHONE_NUMBER", number)
        .containsEntry("CITY_CODE", citycode)
        .containsEntry("COUNTRY_CODE", contrycode);
  }

  @Test
  void accentedTextSurvivesTheRoundTripToTheDatabase() {
    String name = "Ñandú Pérez Müller";

    Reply reply = timed(body("name", quote(name)));

    assertThat(reply.status()).isEqualTo(201);
    assertThat(reply.json().get("name").asString()).isEqualTo(name);
    UUID id = UUID.fromString(reply.json().get("id").asString());
    assertThat(jdbc.queryForObject("SELECT name FROM users WHERE id = ?", String.class, id))
        .isEqualTo(name);
  }
}
