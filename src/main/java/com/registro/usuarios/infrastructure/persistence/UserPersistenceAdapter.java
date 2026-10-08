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
 * Implementa el puerto {@link UserRepository} con Spring Data JPA.
 *
 * <p>La restricción de unicidad del correo es la garantía real de unicidad: la comprobación previa
 * del caso de uso no puede detener dos peticiones concurrentes que encuentran libre la misma
 * dirección. El insert se vuelca (flush) aquí, dentro del adaptador, para que esa condición de
 * carrera falle en este punto y se informe como el mismo rechazo de dominio que la comprobación
 * previa. Cualquier otro fallo de almacenamiento no es un duplicado y se relanza sin tocarlo.
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

  /**
   * Solo la restricción de unicidad significa un duplicado: la tabla no tiene otra columna única.
   */
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
