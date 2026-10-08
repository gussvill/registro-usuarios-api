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
 * Implementa el puerto {@link RegisterUser}. Orquesta y no decide nada: las reglas viven en el
 * dominio.
 *
 * <p>Es el límite transaccional, la única concesión al framework en esta capa. La clase no es final
 * y {@link #register} es público porque la anotación funciona mediante un proxy. La clase no lleva
 * anotación de estereotipo; la configuración decide cómo se crea.
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
    // Una sola lectura del reloj, con la precisión que almacenan las columnas:
    // el token, la respuesta y la fila guardada deben mostrar el mismo instante.
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

  /**
   * La unión de lo que dice cada regla del dominio, un motivo por regla, distintos por
   * construcción.
   */
  private Set<Reason> violationsOf(RegisterUserCommand command) {
    EnumSet<Reason> violations = EnumSet.noneOf(Reason.class);
    User.nameViolation(command.name()).ifPresent(violations::add);
    Email.violation(command.email(), emailFormat).ifPresent(violations::add);
    Password.violation(command.password(), passwordPolicy).ifPresent(violations::add);
    violations.addAll(User.phoneListViolations(command.phones()));
    return violations;
  }

  /**
   * Solo se invoca cuando ya se sabe que los teléfonos son válidos. Una lista ausente significa que
   * no hay teléfonos.
   */
  private static List<Phone> phonesOf(List<PhoneData> phones) {
    if (phones == null) {
      return List.of();
    }
    return phones.stream()
        .map(phone -> new Phone(phone.number(), phone.cityCode(), phone.countryCode()))
        .toList();
  }
}
