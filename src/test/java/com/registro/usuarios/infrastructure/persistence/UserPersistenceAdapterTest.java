package com.registro.usuarios.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.registro.usuarios.domain.exception.EmailAlreadyRegisteredException;
import com.registro.usuarios.domain.model.Email;
import com.registro.usuarios.domain.model.Phone;
import com.registro.usuarios.domain.model.User;
import com.registro.usuarios.domain.model.UserId;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The adapter against the real script and an in-memory database. Reads go through plain SQL, so the
 * assertions describe what is really stored and not what the entity mapping believes.
 */
@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Import(UserPersistenceAdapter.class)
class UserPersistenceAdapterTest {

  private static final Pattern ANY = Pattern.compile("^.+$");
  private static final Instant NOW = Instant.parse("2026-01-15T10:30:00.123456Z");
  private static final String HASH = "$2a$12$abcdefghijklmnopqrstuuABCDEFGHIJKLMNOPQRSTUVWXYZ01234";

  @Autowired private UserPersistenceAdapter adapter;
  @Autowired private UserJpaRepository jpa;
  @Autowired private EntityManager entityManager;
  @Autowired private JdbcTemplate jdbc;

  private static User.Builder user(String email) {
    return User.registration()
        .id(UserId.generate())
        .name("Juan Rodriguez")
        .email(Email.of(email, ANY))
        .passwordHash(HASH)
        .phones(List.of())
        .token("header.payload.signature")
        .registeredAt(NOW);
  }

  private <T> T stored(String column, Class<T> type, UserId id) {
    return jdbc.queryForObject("SELECT " + column + " FROM users WHERE id = ?", type, id.value());
  }

  @Test
  void everyColumnIsStoredAtItsMaximumLengthWithoutTruncation() {
    String name = "n".repeat(User.NAME_MAX_LENGTH);
    String email = "a".repeat(Email.MAX_LENGTH - "@dominio.cl".length()) + "@dominio.cl";
    String hash = "h".repeat(100);
    String token = "t".repeat(1024);
    Phone phone =
        new Phone(
            "1".repeat(Phone.NUMBER_MAX_LENGTH),
            "2".repeat(Phone.CODE_MAX_LENGTH),
            "3".repeat(Phone.CODE_MAX_LENGTH));
    User user =
        user(email).name(name).passwordHash(hash).token(token).phones(List.of(phone)).build();

    adapter.save(user);

    assertThat(stored("name", String.class, user.id())).isEqualTo(name).hasSize(255);
    assertThat(stored("email", String.class, user.id())).isEqualTo(email).hasSize(254);
    assertThat(stored("password_hash", String.class, user.id())).isEqualTo(hash).hasSize(100);
    assertThat(stored("token", String.class, user.id())).isEqualTo(token).hasSize(1024);
    assertThat(stored("is_active", Boolean.class, user.id())).isTrue();
    assertThat(
            jdbc.queryForMap(
                "SELECT phone_number, city_code, country_code FROM phones WHERE user_id = ?",
                user.id().value()))
        .containsEntry("PHONE_NUMBER", "1".repeat(20))
        .containsEntry("CITY_CODE", "2".repeat(10))
        .containsEntry("COUNTRY_CODE", "3".repeat(10));
  }

  @Test
  void aTypicalUserIsStoredWithItsOwnValues() {
    User user =
        user("juan@rodriguez.org")
            .phones(List.of(new Phone("1234567", "1", "57")))
            .token("a.b.c")
            .build();

    adapter.save(user);

    assertThat(stored("id", UUID.class, user.id())).isEqualTo(user.id().value());
    assertThat(stored("name", String.class, user.id())).isEqualTo("Juan Rodriguez");
    assertThat(stored("email", String.class, user.id())).isEqualTo("juan@rodriguez.org");
    assertThat(stored("password_hash", String.class, user.id())).isEqualTo(HASH);
    assertThat(stored("token", String.class, user.id())).isEqualTo("a.b.c");
  }

  @Test
  void phonesAreStoredInTheOrderTheyWereSubmitted() {
    User user =
        user("juan@rodriguez.org")
            .phones(
                List.of(
                    new Phone("2222222", "2", "56"),
                    new Phone("1111111", "1", "57"),
                    new Phone("3333333", "3", "54")))
            .build();

    adapter.save(user);

    assertThat(
            jdbc.queryForList(
                "SELECT phone_number FROM phones WHERE user_id = ? ORDER BY id",
                String.class,
                user.id().value()))
        .containsExactly("2222222", "1111111", "3333333");
  }

  @Test
  void aUserWithoutPhonesLeavesNoPhoneRows() {
    User withPhone =
        user("maria@rodriguez.org").phones(List.of(new Phone("1234567", "1", "57"))).build();
    User withoutPhones = user("juan@rodriguez.org").build();

    adapter.save(withPhone);
    adapter.save(withoutPhones);

    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM phones", Integer.class)).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT user_id FROM phones", UUID.class))
        .isEqualTo(withPhone.id().value());
  }

  @Test
  void timestampsAreStoredAtMicrosecondPrecisionEqualToTheAggregate() {
    Instant other = Instant.parse("2031-12-31T23:59:59.999999Z");
    User first = user("juan@rodriguez.org").registeredAt(NOW).build();
    User second = user("maria@rodriguez.org").registeredAt(other).build();

    adapter.save(first);
    adapter.save(second);

    for (String column : List.of("created_at", "modified_at", "last_login_at")) {
      assertThat(stored(column, OffsetDateTime.class, first.id()).toInstant()).isEqualTo(NOW);
      assertThat(stored(column, OffsetDateTime.class, second.id()).toInstant()).isEqualTo(other);
    }
  }

  @Test
  void existsByEmailReportsOnlyStoredAddressesAndIgnoresCase() {
    adapter.save(user("juan@rodriguez.org").build());

    assertThat(adapter.existsByEmail(Email.of("juan@rodriguez.org", ANY))).isTrue();
    assertThat(adapter.existsByEmail(Email.of("JUAN@Rodriguez.ORG", ANY))).isTrue();
    assertThat(adapter.existsByEmail(Email.of("maria@rodriguez.org", ANY))).isFalse();
  }

  @Test
  void aDuplicateEmailIsTranslatedToTheDomainRejection() {
    adapter.save(user("juan@rodriguez.org").build());

    assertThatThrownBy(() -> adapter.save(user("juan@rodriguez.org").build()))
        .isInstanceOf(EmailAlreadyRegisteredException.class);

    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class)).isEqualTo(1);
  }

  @Test
  void aDuplicateThatDiffersOnlyInCaseIsTheSameEmail() {
    adapter.save(user("juan@rodriguez.org").build());

    assertThatThrownBy(() -> adapter.save(user("JUAN@RODRIGUEZ.ORG").build()))
        .isInstanceOf(EmailAlreadyRegisteredException.class);
  }

  @Test
  void aDifferentDataErrorIsNotMistakenForADuplicate() {
    // 1,025 characters do not fit the 1,024-character token column: a data error, not a duplicate.
    User tooLarge = user("juan@rodriguez.org").token("t".repeat(1025)).build();

    assertThatThrownBy(() -> adapter.save(tooLarge))
        .isInstanceOf(DataIntegrityViolationException.class)
        .isNotInstanceOf(EmailAlreadyRegisteredException.class);
  }

  @Test
  void aNewUserIsInsertedWithoutALookupFirst() {
    Statistics statistics =
        entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
    User user =
        user("juan@rodriguez.org")
            .phones(List.of(new Phone("1111111", "1", "57"), new Phone("2222222", "2", "56")))
            .build();
    statistics.clear();

    adapter.save(user);

    // One INSERT for the user and one per phone. A merge would add a SELECT for the user.
    assertThat(statistics.getPrepareStatementCount()).isEqualTo(3);
    assertThat(statistics.getEntityLoadCount()).isZero();
  }

  @Test
  void anEntityIsNotNewOnceItIsPersistedNorOnceItIsLoaded() {
    User user = user("juan@rodriguez.org").build();
    adapter.save(user);

    UserJpaEntity persisted = jpa.findById(user.id().value()).orElseThrow();
    assertThat(persisted.isNew()).isFalse();

    entityManager.clear();
    UserJpaEntity loaded = jpa.findById(user.id().value()).orElseThrow();
    assertThat(loaded).isNotSameAs(persisted);
    assertThat(loaded.isNew()).isFalse();
  }

  @Test
  void theEntitiesNeverPrintTheHashTheTokenOrTheEmail() {
    User user = user("juan@rodriguez.org").token("secret.token.value").build();
    adapter.save(user);

    UserJpaEntity entity = jpa.findById(user.id().value()).orElseThrow();

    assertThat(entity.toString())
        .contains(user.id().value().toString())
        .doesNotContain(HASH)
        .doesNotContain("secret.token.value")
        .doesNotContain("juan@rodriguez.org");
  }
}
