package com.registro.usuarios.application;

import com.registro.usuarios.application.port.RegisterUser;
import com.registro.usuarios.application.port.RegisterUserCommand;
import com.registro.usuarios.application.port.RegisterUserCommand.PhoneData;
import com.registro.usuarios.domain.exception.EmailAlreadyRegisteredException;
import com.registro.usuarios.domain.exception.InvalidUserDataException;
import com.registro.usuarios.domain.model.Email;
import com.registro.usuarios.domain.model.Phone;
import com.registro.usuarios.domain.model.Reason;
import com.registro.usuarios.domain.model.User;
import com.registro.usuarios.domain.model.UserId;
import com.registro.usuarios.domain.policy.Password;
import com.registro.usuarios.domain.policy.PasswordPolicy;
import com.registro.usuarios.domain.port.PasswordHasher;
import com.registro.usuarios.domain.port.TokenIssuer;
import com.registro.usuarios.domain.port.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements the {@link RegisterUser} port. It orchestrates and decides nothing: the rules live in
 * the domain.
 *
 * <p>This is the transaction boundary, which is the one concession to the framework in this layer.
 * The class stays non-final and {@link #register} public because the annotation works through a
 * proxy. The class carries no stereotype annotation; the wiring decides how it is created.
 */
public class RegisterUserUseCase implements RegisterUser {

  private final UserRepository users;
  private final PasswordHasher hasher;
  private final TokenIssuer tokens;
  private final PasswordPolicy passwordPolicy;
  private final Pattern emailFormat;
  private final Clock clock;

  public RegisterUserUseCase(
      UserRepository users,
      PasswordHasher hasher,
      TokenIssuer tokens,
      PasswordPolicy passwordPolicy,
      Pattern emailFormat,
      Clock clock) {
    this.users = users;
    this.hasher = hasher;
    this.tokens = tokens;
    this.passwordPolicy = passwordPolicy;
    this.emailFormat = emailFormat;
    this.clock = clock;
  }

  @Override
  @Transactional
  public User register(RegisterUserCommand command) {
    Set<Reason> violations = violationsOf(command);
    if (!violations.isEmpty()) {
      throw new InvalidUserDataException(violations);
    }
    Email email = Email.of(command.email(), emailFormat);
    List<Phone> phones = phonesOf(command.phones());
    if (users.existsByEmail(email)) {
      throw new EmailAlreadyRegisteredException();
    }
    String passwordHash = hasher.hash(command.password());
    // One reading of the clock, in the precision the columns store: the token, the response and
    // the stored row must show the same instant.
    Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
    UserId id = UserId.generate();
    String token = tokens.issue(id, email, now);
    User user =
        User.registration()
            .id(id)
            .name(command.name())
            .email(email)
            .passwordHash(passwordHash)
            .phones(phones)
            .token(token)
            .registeredAt(now)
            .build();
    users.save(user);
    return user;
  }

  /** The union of what each domain rule says, one reason per rule, distinct by construction. */
  private Set<Reason> violationsOf(RegisterUserCommand command) {
    EnumSet<Reason> violations = EnumSet.noneOf(Reason.class);
    User.nameViolation(command.name()).ifPresent(violations::add);
    Email.violation(command.email(), emailFormat).ifPresent(violations::add);
    Password.violation(command.password(), passwordPolicy).ifPresent(violations::add);
    violations.addAll(User.phoneListViolations(command.phones()));
    return violations;
  }

  /** Only called once the phones are known to be valid. An absent list means no phones. */
  private static List<Phone> phonesOf(List<PhoneData> phones) {
    if (phones == null) {
      return List.of();
    }
    return phones.stream()
        .map(phone -> new Phone(phone.number(), phone.cityCode(), phone.countryCode()))
        .toList();
  }
}
