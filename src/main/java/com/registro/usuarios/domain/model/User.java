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
 * Aggregate root of a registered user, immutable once built.
 *
 * <p>The only way to create one is {@link #registration()}, whose {@link Builder} names every value
 * (three adjacent strings are easy to swap in a positional constructor) and checks the invariants
 * in {@link Builder#build()}. The server-owned fields are not settable: registering sets the three
 * timestamps to one instant and the user as active.
 *
 * <p>{@link #toString()} prints the identifier only, so the hash, the token and the email can never
 * reach a log line through this type.
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

  /** Starts the registration of a new user. */
  public static Builder registration() {
    return new Builder();
  }

  /** The first failing rule for a name: required, then length. */
  public static Optional<Reason> nameViolation(String name) {
    if (name == null || name.isBlank()) {
      return Optional.of(Reason.NAME_REQUIRED);
    }
    return name.length() > NAME_MAX_LENGTH ? Optional.of(Reason.NAME_TOO_LONG) : Optional.empty();
  }

  /** The rule for the size of the phone list, checked before any entry is inspected. */
  public static Optional<Reason> phoneCountViolation(int count) {
    return count > MAX_PHONES ? Optional.of(Reason.PHONES_TOO_MANY) : Optional.empty();
  }

  /**
   * Every rule that a submitted list of phones breaks. An absent list is allowed (no phones). A
   * list with too many entries is rejected as a whole, without looking at its entries. Otherwise
   * each entry is judged: a null entry gives {@link Reason#PHONE_NULL}, any other the reasons of
   * {@link Phone#violations}. The result is a new read-only set of distinct reasons.
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

  /** The phones in the order they were submitted; the list cannot be modified. */
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

  /** Collects the named parts of a registration and validates them as a whole in {@link #build}. */
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

    /** The single instant of the registration: created, modified and last login. */
    public Builder registeredAt(Instant registeredAt) {
      this.registeredAt = registeredAt;
      return this;
    }

    /**
     * Builds the user.
     *
     * @throws IllegalStateException if a part that the caller must always supply is missing
     * @throws InvalidUserDataException if the name or the number of phones breaks a rule
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
