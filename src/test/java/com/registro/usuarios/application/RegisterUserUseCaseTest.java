package com.registro.usuarios.application;

import static com.registro.usuarios.support.Rejections.reasonsOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.registro.usuarios.application.RegisterUserCommand.PhoneData;
import com.registro.usuarios.domain.exception.EmailAlreadyRegisteredException;
import com.registro.usuarios.domain.exception.InvalidUserDataException.Reason;
import com.registro.usuarios.domain.model.Email;
import com.registro.usuarios.domain.model.Phone;
import com.registro.usuarios.domain.model.User;
import com.registro.usuarios.domain.policy.PasswordPolicy;
import com.registro.usuarios.domain.policy.RegexPasswordPolicy;
import com.registro.usuarios.domain.port.UserRepository;
import com.registro.usuarios.support.FakePasswordHasher;
import com.registro.usuarios.support.FakeTokenIssuer;
import com.registro.usuarios.support.InMemoryUserRepository;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

class RegisterUserUseCaseTest {

  private static final Pattern EMAIL_FORMAT =
      Pattern.compile("^[a-z0-9._%+-]+@[a-z0-9-]+(\\.[a-z0-9-]+)*\\.[a-z]{2,}$");
  private static final PasswordPolicy PASSWORD_POLICY =
      new RegexPasswordPolicy(Pattern.compile("^(?=.*[A-Za-z])(?=.*[0-9])\\S{7,72}$"));

  /** Nanosecond precision on purpose: the use case must truncate to what the column stores. */
  private static final Instant CLOCK_INSTANT = Instant.parse("2026-01-15T10:30:00.123456789Z");

  private static final Instant EXPECTED_INSTANT = Instant.parse("2026-01-15T10:30:00.123456Z");

  private InMemoryUserRepository users;
  private FakePasswordHasher hasher;
  private FakeTokenIssuer tokens;
  private RegisterUserUseCase useCase;

  @BeforeEach
  void setUp() {
    users = new InMemoryUserRepository();
    hasher = new FakePasswordHasher();
    tokens = new FakeTokenIssuer();
    useCase = useCaseWith(users, Clock.fixed(CLOCK_INSTANT, ZoneOffset.UTC));
  }

  private RegisterUserUseCase useCaseWith(UserRepository repository, Clock clock) {
    return new RegisterUserUseCase(
        repository, hasher, tokens, PASSWORD_POLICY, EMAIL_FORMAT, clock);
  }

  private static RegisterUserCommand statementCommand() {
    return commandWithEmail("juan@rodriguez.org");
  }

  private static RegisterUserCommand commandWithEmail(String email) {
    return new RegisterUserCommand(
        "Juan Rodriguez", email, "hunter2", List.of(new PhoneData("1234567", "1", "57")));
  }

  @Test
  void registersTheStatementUserAndStoresIt() {
    User user = useCase.register(statementCommand());

    assertThat(user.name()).isEqualTo("Juan Rodriguez");
    assertThat(user.email().value()).isEqualTo("juan@rodriguez.org");
    assertThat(user.phones()).containsExactly(new Phone("1234567", "1", "57"));
    assertThat(user.active()).isTrue();
    assertThat(users.saved()).containsExactly(user);
  }

  @Test
  void generatedFieldsAreServerOwnedAndAllEqualTheTruncatedClockReading() {
    User user = useCase.register(statementCommand());

    assertThat(user.created()).isEqualTo(EXPECTED_INSTANT);
    assertThat(user.modified()).isEqualTo(EXPECTED_INSTANT);
    assertThat(user.lastLogin()).isEqualTo(EXPECTED_INSTANT);
    assertThat(user.id()).isNotNull();
  }

  @Test
  void twoRegistrationsGetDifferentIds() {
    User first = useCase.register(commandWithEmail("ana@rodriguez.org"));
    User second = useCase.register(commandWithEmail("luis@rodriguez.org"));

    assertThat(first.id()).isNotEqualTo(second.id());
  }

  @Test
  void theEmailIsLowerCasedBeforeItIsStored() {
    User user = useCase.register(commandWithEmail("Juan@Rodriguez.ORG"));

    assertThat(user.email().value()).isEqualTo("juan@rodriguez.org");
    assertThat(users.existsByEmail(Email.of("juan@rodriguez.org", EMAIL_FORMAT))).isTrue();
  }

  @Test
  void theRepositoryReceivesTheHashAndNeverTheRawPassword() {
    useCase.register(statementCommand());

    User stored = users.saved().get(0);
    assertThat(stored.passwordHash()).isEqualTo(FakePasswordHasher.expectedHashOf("hunter2"));
    assertThat(stored.passwordHash()).isNotEqualTo("hunter2").doesNotContain("hunter2");
    assertThat(hasher.hashed()).containsExactly("hunter2");
  }

  @Test
  void theTokenIsIssuedForTheNewUserIdAndTheNormalisedEmail() {
    User user = useCase.register(commandWithEmail("Juan@Rodriguez.ORG"));

    assertThat(tokens.issues()).hasSize(1);
    FakeTokenIssuer.Issue issue = tokens.issues().get(0);
    assertThat(issue.subject()).isEqualTo(user.id());
    assertThat(issue.email().value()).isEqualTo("juan@rodriguez.org");
    assertThat(user.token()).isEqualTo("fake-token." + user.id().value() + "." + 1768473000L);
  }

  @Test
  void theClockIsReadOnceSoTheTokenInstantEqualsTheCreatedInstant() {
    AdvancingClock clock = new AdvancingClock(CLOCK_INSTANT);
    RegisterUserUseCase useCaseWithAdvancingClock = useCaseWith(users, clock);

    User user = useCaseWithAdvancingClock.register(statementCommand());

    assertThat(clock.readings).isEqualTo(1);
    assertThat(tokens.issues().get(0).issuedAt()).isEqualTo(user.created());
    assertThat(user.created()).isEqualTo(EXPECTED_INSTANT);
  }

  @Test
  void aDuplicateEmailIsRejectedAndNothingElseIsSaved() {
    useCase.register(statementCommand());

    assertThatThrownBy(() -> useCase.register(statementCommand()))
        .isInstanceOf(EmailAlreadyRegisteredException.class);

    assertThat(users.count()).isEqualTo(1);
    assertThat(hasher.hashed()).hasSize(1);
    assertThat(tokens.issues()).hasSize(1);
  }

  @Test
  void theDuplicateCheckIgnoresCase() {
    useCase.register(statementCommand());

    assertThatThrownBy(() -> useCase.register(commandWithEmail("JUAN@Rodriguez.ORG")))
        .isInstanceOf(EmailAlreadyRegisteredException.class);
    assertThat(users.count()).isEqualTo(1);
  }

  @Test
  void validationPrecedesTheDuplicateCheck() {
    useCase.register(statementCommand());
    var weakPasswordOnExistingEmail =
        new RegisterUserCommand("Juan Rodriguez", "juan@rodriguez.org", "abc12", List.of());

    assertThat(reasonsOf(() -> useCase.register(weakPasswordOnExistingEmail)))
        .containsExactly(Reason.PASSWORD_FORMAT);
    assertThat(users.count()).isEqualTo(1);
  }

  @Test
  void aUniqueViolationAfterTheCheckPassedStillSurfacesAsADuplicate() {
    UserRepository racing =
        new UserRepository() {
          @Override
          public boolean existsByEmail(Email email) {
            return false;
          }

          @Override
          public void save(User user) {
            throw new EmailAlreadyRegisteredException();
          }
        };

    assertThatThrownBy(
            () ->
                useCaseWith(racing, Clock.fixed(CLOCK_INSTANT, ZoneOffset.UTC))
                    .register(statementCommand()))
        .isInstanceOf(EmailAlreadyRegisteredException.class);
  }

  @Test
  void theTransactionBoundaryIsAnAnnotationOnAPublicMethodOfANonFinalClass() throws Exception {
    Method register = RegisterUserUseCase.class.getMethod("register", RegisterUserCommand.class);

    assertThat(register.isAnnotationPresent(Transactional.class)).isTrue();
    assertThat(Modifier.isFinal(RegisterUserUseCase.class.getModifiers())).isFalse();
    assertThat(Modifier.isFinal(register.getModifiers())).isFalse();
  }

  /**
   * A clock that moves on every reading, so a second reading would show up as a different value.
   */
  private static final class AdvancingClock extends Clock {
    private Instant next;
    private int readings;

    AdvancingClock(Instant start) {
      this.next = start;
    }

    @Override
    public Instant instant() {
      readings++;
      Instant current = next;
      next = next.plusMillis(7);
      return current;
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }
  }
}
