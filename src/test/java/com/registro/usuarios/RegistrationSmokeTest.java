package com.registro.usuarios;

import static org.assertj.core.api.Assertions.assertThat;

import com.registro.usuarios.application.RegisterUserUseCase;
import com.registro.usuarios.application.port.RegisterUser;
import com.registro.usuarios.support.FullContextTest;
import com.registro.usuarios.support.RegistrationClient;
import com.registro.usuarios.support.RegistrationClient.Reply;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import jakarta.persistence.EntityManagerFactory;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.support.OpenEntityManagerInViewInterceptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * La aplicación en ejecución, de punta a punta: el walking skeleton que demuestra que el conjunto
 * de dependencias arranca junto sobre la línea fijada de Spring Boot, y los escenarios de registro
 * sobre HTTP real contra la base de datos real. Cada prueba usa su propio correo (o elimina primero
 * el fijo), de modo que las pruebas no dependen de su orden ni de una base de datos vacía
 * compartida con las demás pruebas de contexto completo.
 */
@FullContextTest
class RegistrationSmokeTest {

  @Autowired private Environment environment;
  @Autowired private ApplicationContext context;
  @Autowired private EntityManagerFactory entityManagerFactory;
  @Autowired private JdbcTemplate jdbc;

  @Value("${local.server.port}")
  private int port;

  private static final JsonMapper JSON = new JsonMapper();

  private final HttpClient http =
      HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();

  private RegistrationClient api;

  @BeforeEach
  void connect() {
    api = new RegistrationClient(port);
  }

  @Test
  void hibernateOnlyValidatesTheScriptAndNoSessionStaysOpenInTheView() {
    assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
    assertThat(entityManagerFactory.getProperties())
        .containsEntry("hibernate.hbm2ddl.auto", "validate");
    assertThat(environment.getProperty("spring.jpa.open-in-view")).isEqualTo("false");
    assertThat(context.getBeansOfType(OpenEntityManagerInViewInterceptor.class)).isEmpty();
  }

  @Test
  void theVersionedScriptCreatesBothTables() {
    List<String> tables =
        jdbc.queryForList(
            "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'PUBLIC'",
            String.class);

    assertThat(tables).containsExactlyInAnyOrder("USERS", "PHONES");
  }

  @Test
  void theOpenApiDocumentIsServedAsJson() throws IOException, InterruptedException {
    HttpResponse<String> response = get("/v3/api-docs");

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().firstValue("Content-Type").orElse("")).contains("json");
    assertThat(response.body()).contains("\"openapi\"");
  }

  @Test
  void theOpenApiDocumentCarriesTheConfiguredTitle() throws IOException, InterruptedException {
    HttpResponse<String> response = get("/v3/api-docs");

    assertThat(response.body())
        .contains("\"title\":\"API de registro de usuarios\"")
        .contains("\"version\":\"1.0.0\"");
  }

  @Test
  void theOpenApiDocumentDescribesThePostEndpointAndItsResponses()
      throws IOException, InterruptedException {
    JsonNode operation = openApi().at("/paths/~1api~1v1~1users/post");

    assertThat(operation.isMissingNode()).isFalse();
    assertThat(operation.get("tags").get(0).asString()).isEqualTo("Usuarios");
    assertThat(operation.get("summary").asString()).isEqualTo("Registrar un usuario");
    List<String> statuses = new ArrayList<>(operation.get("responses").propertyNames());
    assertThat(statuses)
        .containsExactlyInAnyOrder("201", "400", "404", "405", "406", "409", "415", "500");
    assertThat(operation.at("/responses/201/content/application~1json/schema/$ref").asString())
        .endsWith("/UserResponse");
    for (String error : List.of("400", "404", "405", "406", "409", "415", "500")) {
      assertThat(
              operation
                  .at("/responses/" + error + "/content/application~1json/schema/$ref")
                  .asString())
          .endsWith("/ErrorResponse");
    }
  }

  @Test
  void theOpenApiDescribesTheFrameworkErrorsWithTheirCatalogueMessages()
      throws IOException, InterruptedException {
    JsonNode responses = openApi().at("/paths/~1api~1v1~1users/post/responses");

    assertThat(responses.at("/404/content/application~1json").toString())
        .contains("Recurso no encontrado");
    assertThat(responses.at("/405/content/application~1json").toString())
        .contains("Método no permitido");
    assertThat(responses.at("/406/content/application~1json").toString())
        .contains("Formato de respuesta no aceptable");
  }

  @Test
  void theOpenApiTextIsWrittenInSpanish() throws IOException, InterruptedException {
    JsonNode document = openApi();
    JsonNode operation = document.at("/paths/~1api~1v1~1users/post");

    assertThat(document.at("/info/description").asString()).startsWith("Registra un usuario");
    assertThat(operation.get("description").asString()).startsWith("Registra un usuario");
    assertThat(operation.at("/responses/201/description").asString())
        .isEqualTo("Usuario registrado");
    assertThat(operation.at("/responses/415/description").asString())
        .isEqualTo("El cuerpo de la solicitud no es application/json");
    assertThat(document.at("/components/schemas/RegisterUserRequest/description").asString())
        .isEqualTo("Datos del usuario que se registra");
    assertThat(document.at("/components/schemas/ErrorResponse/description").asString())
        .isEqualTo("Cuerpo de todo error: un único mensaje");
  }

  @Test
  void theOpenApiRequestExampleIsTheLiteralStatementBodyAndTheErrorExampleIsTheDuplicate()
      throws IOException, InterruptedException {
    JsonNode operation = openApi().at("/paths/~1api~1v1~1users/post");

    String example =
        operation
            .at("/requestBody/content/application~1json/examples")
            .properties()
            .iterator()
            .next()
            .getValue()
            .get("value")
            .toString();
    assertThat(example)
        .contains("\"contrycode\":\"57\"")
        .contains("\"citycode\":\"1\"")
        .contains("juan@rodriguez.org");
    assertThat(operation.at("/responses/409/content/application~1json").toString())
        .contains("El correo ya registrado");
  }

  @Test
  void theOpenApiSchemasUseTheLiteralNamesAndTheDomainLimits()
      throws IOException, InterruptedException {
    JsonNode schemas = openApi().at("/components/schemas");

    assertThat(keys(schemas.get("UserResponse").get("properties")))
        .containsExactlyInAnyOrder(
            "id",
            "name",
            "email",
            "phones",
            "created",
            "modified",
            "last_login",
            "token",
            "isactive");
    assertThat(keys(schemas.get("PhoneResponse").get("properties")))
        .containsExactlyInAnyOrder("number", "citycode", "contrycode");
    assertThat(keys(schemas.get("PhoneRequest").get("properties")))
        .containsExactlyInAnyOrder("number", "citycode", "contrycode");
    assertThat(keys(schemas.get("ErrorResponse").get("properties"))).containsExactly("mensaje");
    JsonNode request = schemas.get("RegisterUserRequest").get("properties");
    assertThat(request.get("name").get("maxLength").asInt()).isEqualTo(255);
    assertThat(request.get("email").get("maxLength").asInt()).isEqualTo(254);
    assertThat(request.get("password").get("maxLength").asInt()).isEqualTo(72);
    assertThat(schemas.get("PhoneRequest").get("properties").get("number").get("maxLength").asInt())
        .isEqualTo(20);
    assertThat(
            schemas
                .get("PhoneRequest")
                .get("properties")
                .get("contrycode")
                .get("maxLength")
                .asInt())
        .isEqualTo(10);
    JsonNode phone = schemas.get("PhoneRequest").get("properties");
    assertThat(phone.get("number").get("pattern").asString()).isEqualTo("^[0-9]+$");
    assertThat(phone.get("citycode").get("pattern").asString()).isEqualTo("^[0-9]+$");
    assertThat(phone.get("contrycode").get("pattern").asString()).isEqualTo("^\\+?[0-9]+$");
    assertThat(
            schemas.get("UserResponse").get("properties").get("created").get("format").asString())
        .isEqualTo("date-time");
  }

  @Test
  void theOpenApiBoundsThePhoneListWithTheDomainLimit() throws IOException, InterruptedException {
    JsonNode phones = openApi().at("/components/schemas/RegisterUserRequest/properties/phones");

    assertThat(phones.get("type").asString()).isEqualTo("array");
    assertThat(phones.get("maxItems").asInt()).isEqualTo(10);
    assertThat(phones.get("description").asString()).contains("10");
  }

  @Test
  void theOpenApiMarksTheResponseFieldsThatAreAlwaysPresentAsRequired()
      throws IOException, InterruptedException {
    JsonNode schemas = openApi().at("/components/schemas");

    assertThat(requiredOf(schemas.get("UserResponse")))
        .containsExactlyInAnyOrderElementsOf(keys(schemas.get("UserResponse").get("properties")));
    assertThat(requiredOf(schemas.get("PhoneResponse")))
        .containsExactlyInAnyOrder("number", "citycode", "contrycode");
    assertThat(requiredOf(schemas.get("ErrorResponse"))).containsExactly("mensaje");
  }

  @Test
  void theRealResponsesCarryEveryPropertyTheirSchemaDeclaresRequired()
      throws IOException, InterruptedException {
    JsonNode schemas = openApi().at("/components/schemas");
    Reply created = api.post(RegistrationClient.validBody(RegistrationClient.uniqueEmail()));
    Reply rejected = api.post("{}");

    assertThat(created.status()).isEqualTo(201);
    assertThat(keys(created.json()))
        .containsExactlyInAnyOrderElementsOf(requiredOf(schemas.get("UserResponse")));
    assertThat(keys(created.json().get("phones").get(0)))
        .containsExactlyInAnyOrderElementsOf(requiredOf(schemas.get("PhoneResponse")));
    assertThat(rejected.status()).isEqualTo(400);
    assertThat(keys(rejected.json()))
        .containsExactlyInAnyOrderElementsOf(requiredOf(schemas.get("ErrorResponse")));
  }

  @Test
  void theRegistrationUseCaseIsATransactionalProxyOfTheApplicationClass() {
    RegisterUser useCase = context.getBean(RegisterUser.class);

    assertThat(AopUtils.isCglibProxy(useCase)).isTrue();
    assertThat(AopUtils.getTargetClass(useCase)).isEqualTo(RegisterUserUseCase.class);
  }

  @Test
  void swaggerUiIsServedAsHtmlAfterRedirects() throws IOException, InterruptedException {
    HttpResponse<String> response = get("/swagger-ui.html");

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().firstValue("Content-Type").orElse("")).contains("text/html");
    assertThat(response.body()).containsIgnoringCase("swagger");
  }

  // ---------------------------------------------------------------------------------------------
  // Registro sobre HTTP real
  // ---------------------------------------------------------------------------------------------

  private static final Pattern CANONICAL_UUID =
      Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
  private static final Pattern BCRYPT_SHAPE = Pattern.compile("^\\$2[aby]\\$\\d{2}\\$.{53}$");
  private static final String FIXED_INSTANT = FullContextTest.FIXED_INSTANT.toString();

  private static void assertContractBody(Reply reply, int status, String mensaje) {
    assertThat(reply.status()).as("estado").isEqualTo(status);
    assertThat(reply.contentType()).startsWith("application/json");
    JsonNode body = reply.json();
    assertThat(keys(body)).containsExactly("mensaje");
    assertThat(body.get("mensaje").asString()).isEqualTo(mensaje);
  }

  private Map<String, Object> userRow(String id) {
    return jdbc.queryForMap("SELECT * FROM users WHERE id = ?", UUID.fromString(id));
  }

  private int countUsersWithEmail(String email) {
    return jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email = ?", Integer.class, email);
  }

  private static Instant instantOf(Object column) {
    return ((OffsetDateTime) column).toInstant();
  }

  @Test
  void theStatementBodyIsAnswered201WithTheNineDocumentedKeysAndServerGeneratedValues()
      throws Exception {
    RegistrationClient.forget(jdbc, "juan@rodriguez.org");

    Reply reply =
        api.send(
            "POST",
            "/api/v1/users",
            "application/json",
            "application/json",
            RegistrationClient.STATEMENT_BODY);

    assertThat(reply.status()).isEqualTo(201);
    assertThat(reply.contentType()).startsWith("application/json");
    JsonNode body = reply.json();
    assertThat(keys(body))
        .containsExactly(
            "id",
            "name",
            "email",
            "phones",
            "created",
            "modified",
            "last_login",
            "token",
            "isactive");
    assertThat(body.get("name").asString()).isEqualTo("Juan Rodriguez");
    assertThat(body.get("email").asString()).isEqualTo("juan@rodriguez.org");
    assertThat(body.get("phones").toString())
        .isEqualTo("[{\"number\":\"1234567\",\"citycode\":\"1\",\"contrycode\":\"57\"}]");
    String id = body.get("id").asString();
    assertThat(id).matches(CANONICAL_UUID);
    assertThat(UUID.fromString(id)).hasToString(id);
    assertThat(body.get("created").asString()).isEqualTo(FIXED_INSTANT);
    assertThat(body.get("modified").asString()).isEqualTo(FIXED_INSTANT);
    assertThat(body.get("last_login").asString()).isEqualTo(FIXED_INSTANT);
    assertThat(body.get("isactive").isBoolean()).isTrue();
    assertThat(body.get("isactive").asBoolean()).isTrue();
    assertThat(reply.body()).doesNotContain("hunter2").doesNotContain("$2a$");
  }

  // El reloj del parser de JJWT y los accesores de claims se expresan en java.util.Date.
  @SuppressWarnings("JavaUtilDate")
  @Test
  void theTokenIssuedByJackson3ResponseIsVerifiedByJjwtAndCarriesTheDocumentedClaims()
      throws Exception {
    String email = RegistrationClient.uniqueEmail();

    JsonNode body = api.post(RegistrationClient.validBody(email)).json();

    SecretKey key =
        Keys.hmacShaKeyFor(FullContextTest.TOKEN_SECRET.getBytes(StandardCharsets.UTF_8));
    Date fixedNow = Date.from(FullContextTest.FIXED_INSTANT);
    var parsed =
        Jwts.parser()
            .verifyWith(key)
            .clock(() -> fixedNow)
            .build()
            .parseSignedClaims(body.get("token").asString());
    Claims claims = parsed.getPayload();
    assertThat(parsed.getHeader().getAlgorithm()).isEqualTo("HS256");
    assertThat(claims.getSubject()).isEqualTo(body.get("id").asString());
    assertThat(claims.get("email", String.class)).isEqualTo(email);
    assertThat(claims.getIssuedAt().getTime() / 1000)
        .isEqualTo(FullContextTest.FIXED_INSTANT.getEpochSecond());
    assertThat(claims.getExpiration().getTime() / 1000)
        .isEqualTo(FullContextTest.FIXED_INSTANT.getEpochSecond() + 3600);
  }

  @Test
  void theIssuedTokenDoesNotVerifyWithAnotherSecret() throws Exception {
    JsonNode body = api.post(RegistrationClient.validBody(RegistrationClient.uniqueEmail())).json();
    SecretKey other =
        Keys.hmacShaKeyFor(
            "another-secret-0123456789-abcdefghijklmnopqr".getBytes(StandardCharsets.UTF_8));
    String token = body.get("token").asString();

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> Jwts.parser().verifyWith(other).build().parseSignedClaims(token))
        .isInstanceOf(SignatureException.class);
  }

  // El reloj del parser de JJWT y los accesores de claims se expresan en java.util.Date.
  @SuppressWarnings("JavaUtilDate")
  @Test
  void theStoredRowEqualsTheResponseAndTheEmailIsStoredAndSignedLowerCased() throws Exception {
    String mixedCase = "Juan." + UUID.randomUUID() + "@Rodriguez.ORG";

    JsonNode body = api.post(RegistrationClient.validBody(mixedCase)).json();

    String lower = mixedCase.toLowerCase(java.util.Locale.ROOT);
    assertThat(body.get("email").asString()).isEqualTo(lower);
    Map<String, Object> row = userRow(body.get("id").asString());
    assertThat(row.get("NAME")).isEqualTo(body.get("name").asString());
    assertThat(row.get("EMAIL")).isEqualTo(lower);
    assertThat(row.get("TOKEN")).isEqualTo(body.get("token").asString());
    assertThat(row.get("IS_ACTIVE")).isEqualTo(true);
    assertThat(instantOf(row.get("CREATED_AT"))).hasToString(body.get("created").asString());
    assertThat(instantOf(row.get("MODIFIED_AT"))).hasToString(body.get("modified").asString());
    assertThat(instantOf(row.get("LAST_LOGIN_AT"))).hasToString(body.get("last_login").asString());
    SecretKey key =
        Keys.hmacShaKeyFor(FullContextTest.TOKEN_SECRET.getBytes(StandardCharsets.UTF_8));
    Date fixedNow = Date.from(FullContextTest.FIXED_INSTANT);
    Claims claims =
        Jwts.parser()
            .verifyWith(key)
            .clock(() -> fixedNow)
            .build()
            .parseSignedClaims((String) row.get("TOKEN"))
            .getPayload();
    assertThat(claims.get("email", String.class)).isEqualTo(lower);
  }

  @Test
  void thePasswordIsStoredOnlyAsABcryptHashThatVerifiesTheOriginal() throws Exception {
    JsonNode body = api.post(RegistrationClient.validBody(RegistrationClient.uniqueEmail())).json();

    Map<String, Object> row = userRow(body.get("id").asString());
    String hash = (String) row.get("PASSWORD_HASH");
    assertThat(hash).matches(BCRYPT_SHAPE).isNotEqualTo("hunter2");
    assertThat(new BCryptPasswordEncoder().matches("hunter2", hash)).isTrue();
    assertThat(new BCryptPasswordEncoder().matches("hunter3", hash)).isFalse();
    assertThat(row.values().stream().map(String::valueOf)).noneMatch("hunter2"::equals);
  }

  @Test
  void twoUsersWithTheSamePasswordHaveDifferentHashesAndDifferentIds() throws Exception {
    JsonNode first =
        api.post(RegistrationClient.validBody(RegistrationClient.uniqueEmail())).json();
    JsonNode second =
        api.post(RegistrationClient.validBody(RegistrationClient.uniqueEmail())).json();

    assertThat(first.get("id").asString()).isNotEqualTo(second.get("id").asString());
    assertThat(first.get("id").asString()).matches(CANONICAL_UUID);
    assertThat(second.get("id").asString()).matches(CANONICAL_UUID);
    assertThat(userRow(first.get("id").asString()).get("PASSWORD_HASH"))
        .isNotEqualTo(userRow(second.get("id").asString()).get("PASSWORD_HASH"));
  }

  @Test
  void phonesAreReturnedAndStoredInSubmittedOrderLinkedToTheUser() throws Exception {
    ObjectNode request = RegistrationClient.validBody(RegistrationClient.uniqueEmail());
    request
        .putArray("phones")
        .addObject()
        .put("number", "1111111")
        .put("citycode", "2")
        .put("contrycode", "56");
    ((tools.jackson.databind.node.ArrayNode) request.get("phones"))
        .addObject()
        .put("number", "2222222")
        .put("citycode", "9")
        .put("contrycode", "34");

    JsonNode body = api.post(request).json();

    assertThat(body.at("/phones/0/number").asString()).isEqualTo("1111111");
    assertThat(body.at("/phones/1/number").asString()).isEqualTo("2222222");
    List<Map<String, Object>> rows =
        jdbc.queryForList(
            "SELECT phone_number, city_code, country_code FROM phones WHERE user_id = ? ORDER BY id",
            UUID.fromString(body.get("id").asString()));
    assertThat(rows).hasSize(2);
    assertThat(rows.get(0))
        .containsEntry("PHONE_NUMBER", "1111111")
        .containsEntry("CITY_CODE", "2")
        .containsEntry("COUNTRY_CODE", "56");
    assertThat(rows.get(1))
        .containsEntry("PHONE_NUMBER", "2222222")
        .containsEntry("CITY_CODE", "9")
        .containsEntry("COUNTRY_CODE", "34");
  }

  @Test
  void aBodyWithoutPhonesIsAnswered201WithAnEmptyArrayAndStoresNoPhoneRow() throws Exception {
    ObjectNode request = RegistrationClient.validBody(RegistrationClient.uniqueEmail());
    request.remove("phones");

    JsonNode body = api.post(request).json();

    assertThat(body.get("phones").isArray()).isTrue();
    assertThat(body.get("phones")).isEmpty();
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM phones WHERE user_id = ?",
                Integer.class,
                UUID.fromString(body.get("id").asString())))
        .isZero();
  }

  @Test
  void clientSuppliedGeneratedFieldsAreIgnoredEndToEnd() throws Exception {
    ObjectNode request = RegistrationClient.validBody(RegistrationClient.uniqueEmail());
    String suppliedId = "00000000-0000-0000-0000-000000000001";
    request.put("id", suppliedId);
    request.put("token", "client.supplied.token");
    request.put("isactive", false);
    request.put("created", "1999-01-01T00:00:00Z");
    request.put("modified", "1999-01-01T00:00:00Z");
    request.put("last_login", "1999-01-01T00:00:00Z");

    Reply reply = api.post(request);

    assertThat(reply.status()).isEqualTo(201);
    JsonNode body = reply.json();
    assertThat(body.get("id").asString()).isNotEqualTo(suppliedId);
    assertThat(body.get("token").asString()).isNotEqualTo("client.supplied.token");
    assertThat(body.get("isactive").asBoolean()).isTrue();
    assertThat(body.get("created").asString()).isEqualTo(FIXED_INSTANT);
    assertThat(userRow(body.get("id").asString()).get("IS_ACTIVE")).isEqualTo(true);
  }

  @ParameterizedTest(name = "Accept: {0}")
  @ValueSource(strings = {"*/*", "application/json", "application/json;q=0.9, text/html;q=0.1"})
  void everyAcceptThatAllowsJsonIsServed(String accept) throws Exception {
    Reply reply =
        api.send(
            "POST",
            "/api/v1/users",
            "application/json",
            accept,
            RegistrationClient.validBody(RegistrationClient.uniqueEmail()).toString());

    assertThat(reply.status()).isEqualTo(201);
    assertThat(reply.contentType()).startsWith("application/json");
  }

  @Test
  void aRequestWithNoAcceptHeaderIsServed() throws Exception {
    Reply reply = api.post(RegistrationClient.validBody(RegistrationClient.uniqueEmail()));

    assertThat(reply.status()).isEqualTo(201);
    assertThat(reply.contentType()).startsWith("application/json");
  }

  // --- duplicados ---

  @Test
  void repeatingTheStatementBodyIs409WithTheExactBodyAndOneRow() throws Exception {
    RegistrationClient.forget(jdbc, "juan@rodriguez.org");
    assertThat(api.post(RegistrationClient.STATEMENT_BODY).status()).isEqualTo(201);

    Reply second = api.post(RegistrationClient.STATEMENT_BODY);

    assertContractBody(second, 409, "El correo ya registrado");
    assertThat(second.body()).isEqualTo("{\"mensaje\":\"El correo ya registrado\"}");
    assertThat(countUsersWithEmail("juan@rodriguez.org")).isEqualTo(1);
  }

  @Test
  void theSameAddressInAnotherCaseIsADuplicate() throws Exception {
    String email = RegistrationClient.uniqueEmail();
    assertThat(api.post(RegistrationClient.validBody(email)).status()).isEqualTo(201);

    Reply second = api.post(RegistrationClient.validBody(email.toUpperCase(java.util.Locale.ROOT)));

    assertContractBody(second, 409, "El correo ya registrado");
    assertThat(countUsersWithEmail(email)).isEqualTo(1);
  }

  @Test
  void validationIsCheckedBeforeTheDuplicate() throws Exception {
    String email = RegistrationClient.uniqueEmail();
    assertThat(api.post(RegistrationClient.validBody(email)).status()).isEqualTo(201);
    ObjectNode weak = RegistrationClient.validBody(email);
    weak.put("password", "abc12");

    Reply reply = api.post(weak);

    assertContractBody(reply, 400, "La contraseña no cumple el formato requerido");
  }

  @Test
  void twoConcurrentIdenticalRegistrationsAnswerOneCreatedAndOneConflictNeverAServerError()
      throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      for (int round = 0; round < 5; round++) {
        String email = RegistrationClient.uniqueEmail();
        String body = RegistrationClient.validBody(email).toString();
        CyclicBarrier together = new CyclicBarrier(2);
        List<Future<Reply>> replies = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
          replies.add(
              pool.submit(
                  () -> {
                    together.await(10, TimeUnit.SECONDS);
                    return api.post(body);
                  }));
        }

        List<Integer> statuses = new ArrayList<>();
        for (Future<Reply> future : replies) {
          Reply reply = future.get(30, TimeUnit.SECONDS);
          statuses.add(reply.status());
          if (reply.status() == 409) {
            assertThat(reply.body()).isEqualTo("{\"mensaje\":\"El correo ya registrado\"}");
          }
        }

        assertThat(statuses).as("ronda " + round).containsExactlyInAnyOrder(201, 409);
        assertThat(countUsersWithEmail(email)).isEqualTo(1);
      }
    } finally {
      pool.shutdownNow();
    }
  }

  // --- el contrato de errores de punta a punta ---

  @ParameterizedTest(name = "{0}")
  @MethodSource("errorRows")
  void everyRowOfTheErrorTableIsAnsweredOverRealHttp(
      String label,
      String method,
      String path,
      String contentType,
      String accept,
      String body,
      int status,
      String mensaje)
      throws Exception {
    Reply reply = api.send(method, path, contentType, accept, body);

    assertContractBody(reply, status, mensaje);
  }

  static Stream<Arguments> errorRows() {
    String json = "application/json";
    String valid = RegistrationClient.STATEMENT_BODY;
    return Stream.of(
        Arguments.of(
            "validation, all three missing",
            "POST",
            "/api/v1/users",
            json,
            null,
            "{}",
            400,
            "El correo es obligatorio; El nombre es obligatorio; La contraseña es obligatoria"),
        Arguments.of(
            "malformed body",
            "POST",
            "/api/v1/users",
            json,
            null,
            "{\"name\":",
            400,
            "El cuerpo de la solicitud no es válido"),
        Arguments.of(
            "wrong type beats validation",
            "POST",
            "/api/v1/users",
            json,
            null,
            "{\"name\":123}",
            400,
            "El cuerpo de la solicitud no es válido"),
        Arguments.of(
            "empty body",
            "POST",
            "/api/v1/users",
            json,
            null,
            "",
            400,
            "El cuerpo de la solicitud no es válido"),
        Arguments.of(
            "unknown route",
            "GET",
            "/api/v1/does-not-exist",
            null,
            null,
            null,
            404,
            "Recurso no encontrado"),
        Arguments.of(
            "GET on users", "GET", "/api/v1/users", null, null, null, 405, "Método no permitido"),
        Arguments.of(
            "PUT on users", "PUT", "/api/v1/users", json, null, "{}", 405, "Método no permitido"),
        Arguments.of(
            "DELETE on users",
            "DELETE",
            "/api/v1/users",
            null,
            null,
            null,
            405,
            "Método no permitido"),
        Arguments.of(
            "Accept xml",
            "POST",
            "/api/v1/users",
            json,
            "application/xml",
            valid,
            406,
            "Formato de respuesta no aceptable"),
        Arguments.of(
            "Accept html",
            "POST",
            "/api/v1/users",
            json,
            "text/html",
            valid,
            406,
            "Formato de respuesta no aceptable"),
        Arguments.of(
            "Content-Type text/plain",
            "POST",
            "/api/v1/users",
            "text/plain",
            null,
            valid,
            415,
            "Tipo de contenido no soportado"),
        Arguments.of(
            "Content-Type xml",
            "POST",
            "/api/v1/users",
            "application/xml",
            null,
            valid,
            415,
            "Tipo de contenido no soportado"),
        Arguments.of(
            "multipart without boundary on the registration route",
            "POST",
            "/api/v1/users",
            "multipart/form-data",
            null,
            "x",
            415,
            "Tipo de contenido no soportado"),
        Arguments.of(
            "multipart without boundary on an unknown route",
            "POST",
            "/nope",
            "multipart/form-data",
            null,
            "x",
            404,
            "Recurso no encontrado"));
  }

  @Test
  void theAllowHeaderOfTheMethodErrorListsPost() throws Exception {
    for (String method : List.of("GET", "PUT", "DELETE")) {
      Reply reply = api.send(method, "/api/v1/users", "application/json", null, null);

      assertThat(reply.status()).isEqualTo(405);
      assertThat(reply.header("Allow")).contains("POST");
    }
  }

  private JsonNode openApi() throws IOException, InterruptedException {
    return JSON.readTree(get("/v3/api-docs").body());
  }

  private static List<String> requiredOf(JsonNode schema) {
    List<String> required = new ArrayList<>();
    schema.path("required").forEach(name -> required.add(name.asString()));
    return required;
  }

  private static List<String> keys(JsonNode node) {
    return new ArrayList<>(node.propertyNames());
  }

  private HttpResponse<String> get(String path) throws IOException, InterruptedException {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
    return http.send(request, HttpResponse.BodyHandlers.ofString());
  }
}
