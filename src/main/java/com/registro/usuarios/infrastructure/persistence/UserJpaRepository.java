package com.registro.usuarios.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data access to the users table. Only the adapter sees it. */
interface UserJpaRepository extends JpaRepository<UserJpaEntity, UUID> {

  boolean existsByEmail(String email);
}
