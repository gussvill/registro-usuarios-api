package com.registro.usuarios.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.metamodel.EntityType;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * El {@code schema.sql} versionado es la única fuente de la estructura de la base de datos. Estas
 * pruebas se ejecutan contra el script real con Hibernate solo validándolo: las entidades deben
 * encajar en las tablas, y la propia base de datos, no la aplicación, debe imponer la unicidad y la
 * clave foránea.
 */
@DataJpaTest
class SchemaConstraintsTest {

  private static final String INSERT_USER =
      "INSERT INTO users (id, name, email, password_hash, token, is_active, created_at,"
          + " modified_at, last_login_at) VALUES (?, 'Juan', ?, 'hash', 'token', TRUE,"
          + " TIMESTAMP WITH TIME ZONE '2026-01-15 10:30:00+00',"
          + " TIMESTAMP WITH TIME ZONE '2026-01-15 10:30:00+00',"
          + " TIMESTAMP WITH TIME ZONE '2026-01-15 10:30:00+00')";
  private static final String INSERT_PHONE =
      "INSERT INTO phones (user_id, phone_number, city_code, country_code)"
          + " VALUES (?, '1234567', '1', '57')";

  @Autowired private JdbcTemplate jdbc;
  @Autowired private EntityManagerFactory entityManagerFactory;
  @Autowired private Environment environment;

  @Test
  void hibernateValidatesBothEntitiesAgainstTheScriptWithoutCreatingAnything() {
    assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
    assertThat(entityManagerFactory.getProperties())
        .containsEntry("hibernate.hbm2ddl.auto", "validate");

    // Sin ninguna entidad mapeada, "validate" no validaría nada: se nombran las dos que deben
    // comprobarse.
    assertThat(entityManagerFactory.getMetamodel().getEntities())
        .extracting(EntityType::getName)
        .containsExactlyInAnyOrder(
            UserJpaEntity.class.getSimpleName(), PhoneJpaEntity.class.getSimpleName());
  }

  @Test
  void theDatabaseRejectsASecondUserWithTheSameEmail() {
    jdbc.update(INSERT_USER, UUID.randomUUID(), "juan@rodriguez.org");

    assertThatThrownBy(() -> jdbc.update(INSERT_USER, UUID.randomUUID(), "juan@rodriguez.org"))
        .isInstanceOf(DuplicateKeyException.class);

    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class)).isEqualTo(1);
  }

  @Test
  void theDatabaseAcceptsASecondUserWithAnotherEmail() {
    jdbc.update(INSERT_USER, UUID.randomUUID(), "juan@rodriguez.org");

    jdbc.update(INSERT_USER, UUID.randomUUID(), "maria@rodriguez.org");

    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class)).isEqualTo(2);
  }

  @Test
  void theDatabaseRejectsAPhoneOfAnUnknownUser() {
    assertThatThrownBy(() -> jdbc.update(INSERT_PHONE, UUID.randomUUID()))
        .isInstanceOf(DataIntegrityViolationException.class);

    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM phones", Integer.class)).isZero();
  }

  @Test
  void theDatabaseAcceptsAPhoneOfAnExistingUser() {
    UUID userId = UUID.randomUUID();
    jdbc.update(INSERT_USER, userId, "juan@rodriguez.org");

    jdbc.update(INSERT_PHONE, userId);

    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM phones", Integer.class)).isEqualTo(1);
  }
}
