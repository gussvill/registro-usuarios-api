package com.registro.usuarios;

import static org.assertj.core.api.Assertions.assertThat;

import com.registro.usuarios.support.FullContextTest;
import com.registro.usuarios.support.RegistrationClient;
import com.registro.usuarios.support.RegistrationClient.Reply;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * A registration is one transaction: when the phones cannot be stored, the user is not stored
 * either. A temporary CHECK constraint on the {@code phones} table makes the phone insert fail
 * after the user insert has already been flushed, which is the order that would leave a partial
 * user behind if the rollback did not work.
 */
@FullContextTest
class AtomicRegistrationTest {

  private static final String REFUSED_NUMBER = "9999999";

  @Autowired private JdbcTemplate jdbc;

  @Value("${local.server.port}")
  private int port;

  private RegistrationClient api;

  @BeforeEach
  void refuseOnePhoneNumber() {
    api = new RegistrationClient(port);
    jdbc.execute(
        "ALTER TABLE phones ADD CONSTRAINT ck_test_refused_number CHECK (phone_number <> '"
            + REFUSED_NUMBER
            + "')");
  }

  @AfterEach
  void restoreTheSchema() {
    jdbc.execute("ALTER TABLE phones DROP CONSTRAINT IF EXISTS ck_test_refused_number");
  }

  private int count(String sql, Object... args) {
    return jdbc.queryForObject(sql, Integer.class, args);
  }

  private static ObjectNode bodyWithPhoneNumbers(String email, String... numbers) {
    ObjectNode body = RegistrationClient.validBody(email);
    ArrayNode phones = body.putArray("phones");
    for (String number : numbers) {
      phones.addObject().put("number", number).put("citycode", "1").put("contrycode", "57");
    }
    return body;
  }

  @Test
  void aFailureWhileStoringThePhonesLeavesNeitherTheUserNorAnyPhone() throws Exception {
    String email = RegistrationClient.uniqueEmail();
    int usersBefore = count("SELECT COUNT(*) FROM users");
    int phonesBefore = count("SELECT COUNT(*) FROM phones");

    Reply reply = api.post(bodyWithPhoneNumbers(email, REFUSED_NUMBER));

    assertThat(reply.status()).isEqualTo(500);
    assertThat(reply.contentType()).startsWith("application/json");
    assertThat(reply.body()).isEqualTo("{\"mensaje\":\"Error interno del servidor\"}");
    assertThat(count("SELECT COUNT(*) FROM users WHERE email = ?", email)).isZero();
    assertThat(count("SELECT COUNT(*) FROM users")).isEqualTo(usersBefore);
    assertThat(count("SELECT COUNT(*) FROM phones")).isEqualTo(phonesBefore);
  }

  @Test
  void aPhoneThatIsStoredBeforeTheFailingOneIsRolledBackToo() throws Exception {
    String email = RegistrationClient.uniqueEmail();
    int phonesBefore = count("SELECT COUNT(*) FROM phones");

    Reply reply = api.post(bodyWithPhoneNumbers(email, "1111111", "2222222", REFUSED_NUMBER));

    assertThat(reply.status()).isEqualTo(500);
    assertThat(count("SELECT COUNT(*) FROM users WHERE email = ?", email)).isZero();
    assertThat(count("SELECT COUNT(*) FROM phones")).isEqualTo(phonesBefore);
    assertThat(count("SELECT COUNT(*) FROM phones WHERE phone_number IN ('1111111', '2222222')"))
        .isZero();
  }

  @Test
  void theSameAddressCanBeRegisteredAfterTheFailedAttempt() throws Exception {
    String email = RegistrationClient.uniqueEmail();
    assertThat(api.post(bodyWithPhoneNumbers(email, REFUSED_NUMBER)).status()).isEqualTo(500);

    Reply retry = api.post(bodyWithPhoneNumbers(email, "1234567"));

    assertThat(retry.status()).isEqualTo(201);
    assertThat(count("SELECT COUNT(*) FROM users WHERE email = ?", email)).isEqualTo(1);
  }

  @Test
  void withTheConstraintInPlaceAnAcceptedNumberIsStoredNormally() throws Exception {
    String email = RegistrationClient.uniqueEmail();

    Reply reply = api.post(bodyWithPhoneNumbers(email, "1111111", "2222222"));

    assertThat(reply.status()).isEqualTo(201);
    assertThat(count("SELECT COUNT(*) FROM users WHERE email = ?", email)).isEqualTo(1);
    assertThat(
            count(
                "SELECT COUNT(*) FROM phones WHERE user_id = (SELECT id FROM users WHERE email = ?)",
                email))
        .isEqualTo(2);
  }
}
