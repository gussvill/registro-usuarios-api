package com.registro.usuarios.domain.model;

import com.registro.usuarios.domain.exception.InvalidUserDataException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Raíz de agregado de un usuario registrado, inmutable una vez construido.
 *
 * <p>La única forma de crear uno es {@link #registration()}, cuyo {@link Builder} nombra cada valor
 * (tres cadenas contiguas son fáciles de intercambiar en un constructor posicional) y comprueba las
 * invariantes en {@link Builder#build()}. Los campos que pertenecen al servidor no se pueden
 * asignar: registrar fija las tres marcas de tiempo en un mismo instante y el usuario como activo.
 *
 * <p>{@link #toString()} imprime solo el identificador, de modo que el hash, el token y el correo
 * nunca puedan llegar a una línea de log a través de este tipo.
 */
public final class User {

  public static final int NAME_MAX_LENGTH = 255;
  public static final int MAX_PHONES = 10;

  private final UserId id;
  private final String name;
  private final Email email;
  private final String passwordHash;
  private final List<Phone> phones;
  private final String token;
  private final Instant created;
  private final Instant modified;
  private final Instant lastLogin;
  private final boolean active;

  private User(Builder builder) {
    this.id = builder.id;
    this.name = builder.name;
    this.email = builder.email;
    this.passwordHash = builder.passwordHash;
    this.phones = List.copyOf(builder.phones);
    this.token = builder.token;
    this.created = builder.registeredAt;
    this.modified = builder.registeredAt;
    this.lastLogin = builder.registeredAt;
    this.active = true;
  }

  /** Inicia el registro de un nuevo usuario. */
  public static Builder registration() {
    return new Builder();
  }

  /** La primera regla incumplida para un nombre: obligatorio y luego longitud. */
  public static Optional<Reason> nameViolation(String name) {
    if (name == null || name.isBlank()) {
      return Optional.of(Reason.NAME_REQUIRED);
    }
    return name.length() > NAME_MAX_LENGTH ? Optional.of(Reason.NAME_TOO_LONG) : Optional.empty();
  }

  /**
   * La regla del tamaño de la lista de teléfonos, comprobada antes de inspeccionar cualquier
   * entrada.
   */
  public static Optional<Reason> phoneCountViolation(int count) {
    return count > MAX_PHONES ? Optional.of(Reason.PHONES_TOO_MANY) : Optional.empty();
  }

  /**
   * Todas las reglas que incumple una lista de teléfonos enviada. Una lista ausente está permitida
   * (sin teléfonos). Una lista con demasiadas entradas se rechaza en su conjunto, sin mirar sus
   * entradas. En otro caso se juzga cada entrada: una entrada nula da {@link Reason#PHONE_NULL},
   * cualquier otra los motivos de {@link Phone#violations}. El resultado es un conjunto nuevo de
   * solo lectura de motivos distintos.
   */
  public static Set<Reason> phoneListViolations(List<? extends PhoneInput> phones) {
    if (phones == null) {
      return Set.of();
    }
    Optional<Reason> tooMany = phoneCountViolation(phones.size());
    if (tooMany.isPresent()) {
      return Set.of(tooMany.get());
    }
    EnumSet<Reason> violations = EnumSet.noneOf(Reason.class);
    for (PhoneInput phone : phones) {
      if (phone == null) {
        violations.add(Reason.PHONE_NULL);
      } else {
        violations.addAll(Phone.violations(phone.number(), phone.cityCode(), phone.countryCode()));
      }
    }
    return Collections.unmodifiableSet(violations);
  }

  public UserId id() {
    return id;
  }

  public String name() {
    return name;
  }

  public Email email() {
    return email;
  }

  public String passwordHash() {
    return passwordHash;
  }

  /** Los teléfonos en el orden en que se enviaron; la lista no se puede modificar. */
  public List<Phone> phones() {
    return phones;
  }

  public String token() {
    return token;
  }

  public Instant created() {
    return created;
  }

  public Instant modified() {
    return modified;
  }

  public Instant lastLogin() {
    return lastLogin;
  }

  public boolean active() {
    return active;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof User user && id.equals(user.id);
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }

  @Override
  public String toString() {
    return "User[id=" + id.value() + "]";
  }

  /** Reúne las partes nombradas de un registro y las valida en conjunto en {@link #build}. */
  public static final class Builder {

    private UserId id;
    private String name;
    private Email email;
    private String passwordHash;
    private List<Phone> phones = List.of();
    private String token;
    private Instant registeredAt;

    private Builder() {}

    public Builder id(UserId id) {
      this.id = id;
      return this;
    }

    public Builder name(String name) {
      this.name = name;
      return this;
    }

    public Builder email(Email email) {
      this.email = email;
      return this;
    }

    public Builder passwordHash(String passwordHash) {
      this.passwordHash = passwordHash;
      return this;
    }

    public Builder phones(List<Phone> phones) {
      this.phones = phones == null ? null : new ArrayList<>(phones);
      return this;
    }

    public Builder token(String token) {
      this.token = token;
      return this;
    }

    /** El instante único del registro: creación, modificación y último acceso. */
    public Builder registeredAt(Instant registeredAt) {
      this.registeredAt = registeredAt;
      return this;
    }

    /**
     * Construye el usuario.
     *
     * @throws IllegalStateException si falta una parte que quien llama siempre debe aportar
     * @throws InvalidUserDataException si el nombre o la cantidad de teléfonos incumple una regla
     */
    public User build() {
      requirePresent(id != null, "id");
      requirePresent(email != null, "email");
      requirePresent(passwordHash != null && !passwordHash.isBlank(), "passwordHash");
      requirePresent(token != null && !token.isBlank(), "token");
      requirePresent(registeredAt != null, "registeredAt");
      requirePresent(phones != null, "phones");
      nameViolation(name)
          .or(() -> phoneCountViolation(phones.size()))
          .ifPresent(
              reason -> {
                throw new InvalidUserDataException(Set.of(reason));
              });
      return new User(this);
    }

    private static void requirePresent(boolean present, String part) {
      if (!present) {
        throw new IllegalStateException("A user cannot be built without " + part);
      }
    }
  }
}
