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
   * Indica si el fallo es una violación de una restricción de unicidad. Se comprueba el tipo de la
   * restricción ({@code ConstraintKind.UNIQUE}) y no su nombre. La tabla {@code users} tiene dos:
   * la del correo ({@code uk_users_email}) y la clave primaria ({@code pk_users}), que también es
   * única, de modo que una colisión del id se informaría igualmente como correo duplicado. No es un
   * camino realista: el id es un UUID aleatorio generado por la aplicación.
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
