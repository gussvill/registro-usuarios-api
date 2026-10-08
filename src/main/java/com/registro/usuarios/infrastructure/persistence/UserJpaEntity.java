package com.registro.usuarios.infrastructure.persistence;

import com.registro.usuarios.domain.model.Email;
import com.registro.usuarios.domain.model.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/**
 * Modelo de persistencia de un usuario, deliberadamente separado del agregado de dominio. Se
 * escribe a partir del agregado y nunca se vuelve a leer hacia él, porque el registro no tiene
 * camino de lectura.
 *
 * <p>El identificador lo asigna la aplicación, así que Spring Data no puede distinguir una fila
 * nueva de una existente con solo mirarla. {@link Persistable} con un indicador transitorio lo dice
 * de forma explícita y hace que {@code save} persista directamente en lugar de emitir un {@code
 * SELECT} para decidir un merge.
 *
 * <p>{@link #toString()} imprime solo el identificador, de modo que el hash, el token y el correo
 * no puedan llegar a una línea de log a través de este tipo.
 */
@Entity
@Table(name = "users")
class UserJpaEntity implements Persistable<UUID> {

  static final int PASSWORD_HASH_LENGTH = 100;
  static final int TOKEN_LENGTH = 1024;

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "name", nullable = false, length = User.NAME_MAX_LENGTH)
  private String name;

  @Column(name = "email", nullable = false, length = Email.MAX_LENGTH)
  private String email;

  @Column(name = "password_hash", nullable = false, length = PASSWORD_HASH_LENGTH)
  private String passwordHash;

  @Column(name = "token", nullable = false, length = TOKEN_LENGTH)
  private String token;

  @Column(name = "is_active", nullable = false)
  private boolean active;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "modified_at", nullable = false)
  private Instant modifiedAt;

  @Column(name = "last_login_at", nullable = false)
  private Instant lastLoginAt;

  @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("id ASC")
  private List<PhoneJpaEntity> phones = new ArrayList<>();

  @Transient private boolean isNew = true;

  /** Requerido por JPA. */
  protected UserJpaEntity() {}

  private UserJpaEntity(User user) {
    this.id = user.id().value();
    this.name = user.name();
    this.email = user.email().value();
    this.passwordHash = user.passwordHash();
    this.token = user.token();
    this.active = user.active();
    this.createdAt = user.created();
    this.modifiedAt = user.modified();
    this.lastLoginAt = user.lastLogin();
  }

  /**
   * Mapeo de solo escritura: entra el agregado, sale la entidad, con los teléfonos en el orden en
   * que se enviaron.
   */
  static UserJpaEntity from(User user) {
    UserJpaEntity entity = new UserJpaEntity(user);
    user.phones()
        .forEach(
            phone ->
                entity.phones.add(
                    new PhoneJpaEntity(
                        entity, phone.number(), phone.cityCode(), phone.countryCode())));
    return entity;
  }

  @Override
  public UUID getId() {
    return id;
  }

  @Override
  public boolean isNew() {
    return isNew;
  }

  @PostPersist
  @PostLoad
  void markNotNew() {
    this.isNew = false;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof UserJpaEntity entity && id != null && id.equals(entity.id);
  }

  @Override
  public int hashCode() {
    return UserJpaEntity.class.hashCode();
  }

  @Override
  public String toString() {
    return "UserJpaEntity[id=" + id + "]";
  }
}
