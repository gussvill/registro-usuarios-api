package com.registro.usuarios;

import static org.assertj.core.api.Assertions.assertThat;

import com.registro.usuarios.application.RegisterUserUseCase;
import com.registro.usuarios.support.FullContextTest;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.persistence.EntityManagerFactory;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.support.OpenEntityManagerInViewInterceptor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Walking skeleton: proves that the whole dependency set boots together on the pinned Spring Boot
 * line, before any design work depends on it.
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
        .contains("\"title\":\"User Registration API\"")
        .contains("\"version\":\"1.0.0\"");
  }

  @Test
  void theOpenApiDocumentDescribesThePostEndpointAndItsResponses()
      throws IOException, InterruptedException {
    JsonNode operation = openApi().at("/paths/~1api~1v1~1users/post");

    assertThat(operation.isMissingNode()).isFalse();
    assertThat(operation.get("tags").get(0).asString()).isEqualTo("Users");
    assertThat(operation.get("summary").asString()).isNotBlank();
    List<String> statuses = new ArrayList<>(operation.get("responses").propertyNames());
    assertThat(statuses).containsExactlyInAnyOrder("201", "400", "409", "415", "500");
    assertThat(operation.at("/responses/201/content/application~1json/schema/$ref").asString())
        .endsWith("/UserResponse");
    for (String error : List.of("400", "409", "415", "500")) {
      assertThat(
              operation
                  .at("/responses/" + error + "/content/application~1json/schema/$ref")
                  .asString())
          .endsWith("/ErrorResponse");
    }
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
    assertThat(
            schemas.get("UserResponse").get("properties").get("created").get("format").asString())
        .isEqualTo("date-time");
  }

  @Test
  void theRegistrationUseCaseIsATransactionalProxyOfTheApplicationClass() {
    RegisterUserUseCase useCase = context.getBean(RegisterUserUseCase.class);

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

  // JJWT's builder and claims API are expressed in java.util.Date.
  @SuppressWarnings("JavaUtilDate")
  @Test
  void aTokenSignedWithJjwtIsParsedBackInsideTheRunningContext() {
    SecretKey key =
        Keys.hmacShaKeyFor(FullContextTest.TOKEN_SECRET.getBytes(StandardCharsets.UTF_8));
    Date issuedAt = Date.from(FullContextTest.FIXED_INSTANT);
    Date expiresAt = new Date(issuedAt.getTime() + 3_600_000L);

    String token =
        Jwts.builder()
            .subject("11111111-2222-3333-4444-555555555555")
            .claim("email", "juan@rodriguez.org")
            .issuedAt(issuedAt)
            .expiration(expiresAt)
            .signWith(key, Jwts.SIG.HS256)
            .compact();

    Claims claims =
        Jwts.parser()
            .verifyWith(key)
            .clock(() -> issuedAt)
            .build()
            .parseSignedClaims(token)
            .getPayload();

    assertThat(claims.getSubject()).isEqualTo("11111111-2222-3333-4444-555555555555");
    assertThat(claims.get("email", String.class)).isEqualTo("juan@rodriguez.org");
    assertThat(claims.getExpiration().getTime() - claims.getIssuedAt().getTime())
        .isEqualTo(3_600_000L);
  }

  private JsonNode openApi() throws IOException, InterruptedException {
    return JSON.readTree(get("/v3/api-docs").body());
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
