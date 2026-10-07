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
 * Persistence model of a user, deliberately separate from the domain aggregate. It is written from
 * the aggregate and never read back into it, because registration has no read path.
 *
 * <p>The identifier is assigned by the application, so Spring Data cannot tell a new row from an
 * existing one by looking at it. {@link Persistable} with a transient flag says so explicitly and
 * makes {@code save} persist directly instead of issuing a {@code SELECT} to decide on a merge.
 *
 * <p>{@link #toString()} prints the identifier only, so the hash, the token and the email cannot
 * reach a log line through this type.
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

  /** Required by JPA. */
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

  /** Write-only mapping: the aggregate in, the entity out, phones in submitted order. */
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
