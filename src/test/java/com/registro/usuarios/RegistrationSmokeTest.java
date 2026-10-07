package com.registro.usuarios;

import static org.assertj.core.api.Assertions.assertThat;

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
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.support.OpenEntityManagerInViewInterceptor;

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

  private final HttpClient http =
      HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();

  @Test
  void hibernateOnlyValidatesTheScriptAndNoSessionStaysOpenInTheView() {
    assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
    assertThat(entityManagerFactory.getProperties()).containsEntry("hibernate.hbm2ddl.auto", "validate");
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
  void swaggerUiIsServedAsHtmlAfterRedirects() throws IOException, InterruptedException {
    HttpResponse<String> response = get("/swagger-ui.html");

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().firstValue("Content-Type").orElse("")).contains("text/html");
    assertThat(response.body()).containsIgnoringCase("swagger");
  }

  @Test
  void aTokenSignedWithJjwtIsParsedBackInsideTheRunningContext() {
    SecretKey key = Keys.hmacShaKeyFor(FullContextTest.TOKEN_SECRET.getBytes(StandardCharsets.UTF_8));
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

  private HttpResponse<String> get(String path) throws IOException, InterruptedException {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
    return http.send(request, HttpResponse.BodyHandlers.ofString());
  }
}
