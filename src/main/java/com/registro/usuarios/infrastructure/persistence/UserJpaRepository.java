package com.registro.usuarios.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acceso Spring Data a la tabla de usuarios; Spring genera la implementación. Solo lo ve el
 * adaptador.
 */
interface UserJpaRepository extends JpaRepository<UserJpaEntity, UUID> {

  boolean existsByEmail(String email);
}
