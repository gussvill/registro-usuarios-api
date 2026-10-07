package com.registro.usuarios.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.registro.usuarios.domain.exception.EmailAlreadyRegisteredException;
import com.registro.usuarios.domain.model.Email;
import com.registro.usuarios.domain.model.User;
import com.registro.usuarios.domain.model.UserId;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;
import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.ConstraintViolationException.ConstraintKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Which storage failures mean "the email is taken" and which do not, decided with hand-built
 * exceptions so that every kind of constraint violation is covered, not only the one the database
 * happens to raise in the other test class.
 */
class UserPersistenceAdapterFailureTest {

  private final UserJpaRepository jpa = mock(UserJpaRepository.class);
  private final UserPersistenceAdapter adapter = new UserPersistenceAdapter(jpa);
  private final User user =
      User.registration()
          .id(UserId.generate())
          .name("Juan Rodriguez")
          .email(Email.of("juan@rodriguez.org", Pattern.compile("^.+$")))
          .passwordHash("hash")
          .phones(List.of())
          .token("a.b.c")
          .registeredAt(Instant.parse("2026-01-15T10:30:00Z"))
          .build();

  private static ConstraintViolationException violation(ConstraintKind kind) {
    return new ConstraintViolationException(
        "could not execute statement", new SQLException("boom"), kind, "some_constraint");
  }

  @Test
  void aUniqueViolationBecomesTheDomainRejection() {
    when(jpa.saveAndFlush(any()))
        .thenThrow(new DataIntegrityViolationException("dup", violation(ConstraintKind.UNIQUE)));

    assertThatThrownBy(() -> adapter.save(user))
        .isInstanceOf(EmailAlreadyRegisteredException.class);
  }

  @Test
  void aUniqueViolationIsFoundEvenWhenItIsWrappedDeeper() {
    when(jpa.saveAndFlush(any()))
        .thenThrow(
            new DataIntegrityViolationException(
                "outer", new IllegalStateException("middle", violation(ConstraintKind.UNIQUE))));

    assertThatThrownBy(() -> adapter.save(user))
        .isInstanceOf(EmailAlreadyRegisteredException.class);
  }

  @ParameterizedTest
  @EnumSource(
      value = ConstraintKind.class,
      names = {"NOT_NULL", "FOREIGN_KEY", "CHECK", "OTHER"})
  void anyOtherKindOfConstraintViolationIsRethrownUnchanged(ConstraintKind kind) {
    DataIntegrityViolationException original =
        new DataIntegrityViolationException("not a duplicate", violation(kind));
    when(jpa.saveAndFlush(any())).thenThrow(original);

    assertThatThrownBy(() -> adapter.save(user)).isSameAs(original);
  }

  @Test
  void aFailureWithoutAHibernateCauseIsRethrownUnchanged() {
    DataIntegrityViolationException original =
        new DataIntegrityViolationException("no cause", new SQLException("boom"));
    when(jpa.saveAndFlush(any())).thenThrow(original);

    assertThatThrownBy(() -> adapter.save(user)).isSameAs(original);
  }

  @Test
  void aFailureWithNoCauseAtAllIsRethrownUnchanged() {
    DataIntegrityViolationException original = new DataIntegrityViolationException("bare");
    when(jpa.saveAndFlush(any())).thenThrow(original);

    assertThatThrownBy(() -> adapter.save(user)).isSameAs(original);
  }

  @Test
  void aNonDataFailureIsNotTouched() {
    IllegalStateException original = new IllegalStateException("broken");
    when(jpa.saveAndFlush(any())).thenThrow(original);

    assertThatThrownBy(() -> adapter.save(user)).isSameAs(original);
  }

  @Test
  void theAdapterFlushesTheInsertSoTheViolationSurfacesInsideIt() {
    adapter.save(user);

    verify(jpa).saveAndFlush(any(UserJpaEntity.class));
  }
}
