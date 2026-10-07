package com.registro.usuarios;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.registro.usuarios.domain.exception.EmailAlreadyRegisteredException;
import com.registro.usuarios.domain.model.Email;
import com.registro.usuarios.domain.model.User;
import com.registro.usuarios.domain.model.UserId;
import com.registro.usuarios.domain.port.UserRepository;
import com.registro.usuarios.support.FullContextTest;
import com.registro.usuarios.support.RegistrationClient;
import com.registro.usuarios.support.RegistrationClient.Reply;
import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The password, its hash, the token and the signing secret never reach the logs, on the success
 * path and on the failure paths, with the application at DEBUG. A second group raises the web
 * framework to TRACE, where it prints the objects it reads and writes: that shows the records
 * redact what they carry.
 */
@FullContextTest
@ExtendWith(OutputCaptureExtension.class)
class LogSecrecyTest {

  private static final String PASSWORD = "hunter2";
  private static final Pattern ANY_JWT =
      Pattern.compile("eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]*");
  private static final Pattern ANY_BCRYPT = Pattern.compile("\\$2[aby]\\$\\d{2}\\$");

  @Autowired private LoggingSystem logging;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private UserRepository users;

  @Value("${local.server.port}")
  private int port;

  private RegistrationClient api;

  @BeforeEach
  void captureAtDebug() {
    api = new RegistrationClient(port);
    logging.setLogLevel("com.registro", LogLevel.DEBUG);
  }

  @AfterEach
  void restoreLevels() {
    logging.setLogLevel("com.registro", null);
    logging.setLogLevel("org.springframework.web", null);
  }

  private static ObjectNode bodyFor(String email) {
    return RegistrationClient.validBody(email);
  }

  private static void assertNoSecret(CapturedOutput output) {
    assertThat(output.getAll())
        .doesNotContain(PASSWORD)
        .doesNotContain(FullContextTest.TOKEN_SECRET)
        .doesNotContainPattern(ANY_JWT)
        .doesNotContainPattern(ANY_BCRYPT);
  }

  // --- success ---

  @Test
  void theSuccessPathLogsOnlyTheMaskedEmailAndNoSecret(CapturedOutput output) throws Exception {
    String email = RegistrationClient.uniqueEmail();

    Reply reply = api.post(bodyFor(email));

    assertThat(reply.status()).isEqualTo(201);
    JsonNode body = reply.json();
    String token = body.get("token").asString();
    String hash =
        jdbc.queryForObject(
            "SELECT password_hash FROM users WHERE id = ?",
            String.class,
            java.util.UUID.fromString(body.get("id").asString()));
    assertThat(output.getAll())
        .doesNotContain(PASSWORD)
        .doesNotContain(token)
        .doesNotContain(hash)
        .doesNotContain(FullContextTest.TOKEN_SECRET);
    assertNoSecret(output);
    // The line exists (so the capture works), carries the id and the masked address only.
    assertThat(output.getAll())
        .contains("User registered")
        .contains(body.get("id").asString())
        .contains("***@dominio.cl")
        .doesNotContain(email)
        .doesNotContain(email.substring(0, email.indexOf('@')));
  }

  // --- failure paths ---

  @Test
  void aDuplicateRegistrationLogsNoSecret(CapturedOutput output) throws Exception {
    String email = RegistrationClient.uniqueEmail();
    assertThat(api.post(bodyFor(email)).status()).isEqualTo(201);

    Reply duplicate = api.post(bodyFor(email));

    assertThat(duplicate.status()).isEqualTo(409);
    assertNoSecret(output);
  }

  /**
   * The race behind the pre-check: two requests both find the address free and the second insert
   * reaches the unique constraint. Hibernate logs a failed statement together with the database's
   * own message, which quotes the offending value; that logger is switched off so the address does
   * not reach the server log.
   */
  @Test
  void aDuplicateThatReachesTheConstraintLogsNeitherTheAddressNorASecret(CapturedOutput output)
      throws Exception {
    String email = RegistrationClient.uniqueEmail();
    assertThat(api.post(bodyFor(email)).status()).isEqualTo(201);
    User second =
        User.registration()
            .id(UserId.generate())
            .name("Juan Rodriguez")
            .email(Email.of(email, Pattern.compile("^.+$")))
            .passwordHash("$2a$12$abcdefghijklmnopqrstuuABCDEFGHIJKLMNOPQRSTUVWXYZ01234")
            .phones(List.of())
            .token("header.payload.signature")
            .registeredAt(Instant.parse("2026-01-15T10:30:00Z"))
            .build();

    assertThatThrownBy(() -> users.save(second))
        .isInstanceOf(EmailAlreadyRegisteredException.class);

    assertThat(output.getAll())
        .doesNotContain(email)
        .doesNotContain(email.substring(0, email.indexOf('@')));
    assertNoSecret(output);
  }

  @Test
  void aValidationFailureLogsNoSecret(CapturedOutput output) throws Exception {
    ObjectNode body = bodyFor("not-an-email");

    Reply reply = api.post(body);

    assertThat(reply.status()).isEqualTo(400);
    assertNoSecret(output);
  }

  @Test
  void aMalformedBodyThatContainsThePasswordLogsNoSecret(CapturedOutput output) throws Exception {
    Reply reply =
        api.post("{\"name\":\"Juan\",\"password\":\"" + PASSWORD + "\" BROKEN {{ \"email\":");

    assertThat(reply.status()).isEqualTo(400);
    assertNoSecret(output);
  }

  @Test
  void aWronglyTypedBodyThatContainsThePasswordLogsNoSecret(CapturedOutput output)
      throws Exception {
    Reply reply = api.post("{\"name\":12345,\"password\":\"" + PASSWORD + "\"}");

    assertThat(reply.status()).isEqualTo(400);
    assertNoSecret(output);
  }

  @Test
  void anUnexpectedFailureIsLoggedWithItsStackTraceButWithoutPayloadOrSecret(CapturedOutput output)
      throws Exception {
    jdbc.execute(
        "ALTER TABLE phones ADD CONSTRAINT ck_log_refused CHECK (phone_number <> '8888888')");
    try {
      ObjectNode body = bodyFor(RegistrationClient.uniqueEmail());
      body.putArray("phones")
          .addObject()
          .put("number", "8888888")
          .put("citycode", "1")
          .put("contrycode", "57");

      Reply reply = api.post(body);

      assertThat(reply.status()).isEqualTo(500);
      assertThat(reply.body()).isEqualTo("{\"mensaje\":\"Error interno del servidor\"}");
      assertThat(output.getAll())
          .contains("Unexpected failure while handling a request")
          .contains("ck_log_refused".toUpperCase(java.util.Locale.ROOT));
      assertNoSecret(output);
    } finally {
      jdbc.execute("ALTER TABLE phones DROP CONSTRAINT IF EXISTS ck_log_refused");
    }
  }

  // --- the framework at TRACE prints what it reads and writes ---

  @Test
  void theRequestAndTheResponseAreRedactedWhenTheWebFrameworkLogsThemAtTrace(CapturedOutput output)
      throws Exception {
    logging.setLogLevel("org.springframework.web", LogLevel.TRACE);

    Reply reply = api.post(bodyFor(RegistrationClient.uniqueEmail()));

    assertThat(reply.status()).isEqualTo(201);
    String token = reply.json().get("token").asString();
    assertThat(output.getAll())
        .contains("RegisterUserRequest[")
        .contains("password=<redacted>")
        .contains("UserResponse[")
        .doesNotContain(PASSWORD)
        .doesNotContain(token);
    assertNoSecret(output);
  }

  @Test
  void aRejectedBodyIsRedactedWhenTheWebFrameworkLogsItAtTrace(CapturedOutput output)
      throws Exception {
    logging.setLogLevel("org.springframework.web", LogLevel.TRACE);

    Reply reply = api.post(bodyFor("not-an-email"));

    assertThat(reply.status()).isEqualTo(400);
    assertThat(output.getAll()).contains("RegisterUserRequest[").contains("password=<redacted>");
    assertNoSecret(output);
  }
}
