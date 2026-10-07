package com.registro.usuarios.infrastructure.persistence;

import com.registro.usuarios.domain.exception.EmailAlreadyRegisteredException;
import com.registro.usuarios.domain.model.Email;
import com.registro.usuarios.domain.model.User;
import com.registro.usuarios.domain.port.UserRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.ConstraintViolationException.ConstraintKind;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

/**
 * Implements the {@link UserRepository} port with Spring Data JPA.
 *
 * <p>The unique constraint on the email is the real guarantee of uniqueness: the use case's
 * pre-check cannot stop two concurrent requests that both find the address free. The insert is
 * flushed here, inside the adapter, so that this race fails at this point and is reported as the
 * same domain rejection as the pre-check. Any other storage failure is not a duplicate and is
 * rethrown untouched.
 */
@Repository
class UserPersistenceAdapter implements UserRepository {

  private final UserJpaRepository jpa;

  UserPersistenceAdapter(UserJpaRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public boolean existsByEmail(Email email) {
    return jpa.existsByEmail(email.value());
  }

  @Override
  public void save(User user) {
    try {
      jpa.saveAndFlush(UserJpaEntity.from(user));
    } catch (DataIntegrityViolationException e) {
      if (isUniqueViolation(e)) {
        throw new EmailAlreadyRegisteredException();
      }
      throw e;
    }
  }

  /** Only the unique constraint means a duplicate: the table has no other unique column. */
  private static boolean isUniqueViolation(Throwable failure) {
    for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
      if (cause instanceof ConstraintViolationException violation
          && violation.getKind() == ConstraintKind.UNIQUE) {
        return true;
      }
    }
    return false;
  }
}
