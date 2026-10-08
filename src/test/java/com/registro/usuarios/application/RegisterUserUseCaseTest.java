package com.registro.usuarios.application;

import static com.registro.usuarios.support.Rejections.reasonsOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.registro.usuarios.application.port.RegisterUserCommand;
import com.registro.usuarios.application.port.RegisterUserCommand.PhoneData;
import com.registro.usuarios.domain.exception.EmailAlreadyRegisteredException;
import com.registro.usuarios.domain.model.Email;
import com.registro.usuarios.domain.model.Phone;
import com.registro.usuarios.domain.model.Reason;
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
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.springframework.transaction.annotation.Transactional;

class RegisterUserUseCaseTest {

  private static final Pattern EMAIL_FORMAT =
      Pattern.compile("^[a-z0-9._%+-]+@[a-z0-9-]+(\\.[a-z0-9-]+)*\\.[a-z]{2,}$");
  private static final PasswordPolicy PASSWORD_POLICY =
      new RegexPasswordPolicy(Pattern.compile("^(?=.*[A-Za-z])(?=.*[0-9])\\S{7,72}$"));

  /**
   * Precisión de nanosegundos a propósito: el caso de uso debe truncar a lo que almacena la
   * columna.
   */
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

  // ---- recolección de violaciones (todas las reglas, todos los campos, un rechazo) ----

  private static RegisterUserCommand command(
      String name, String email, String password, List<PhoneData> phones) {
    return new RegisterUserCommand(name, email, password, phones);
  }

  private static RegisterUserCommand withPhones(List<PhoneData> phones) {
    return command("Juan Rodriguez", "juan@rodriguez.org", "hunter2", phones);
  }

  private static PhoneData phone(String number, String cityCode, String countryCode) {
    return new PhoneData(number, cityCode, countryCode);
  }

  /**
   * Ejecuta el registro, espera exactamente estos motivos y comprueba que no hubo ningún efecto
   * secundario.
   */
  private void assertRejectedWith(RegisterUserCommand command, Reason... expected) {
    assertRejectedWith(useCase, command, expected);
  }

  private void assertRejectedWith(
      RegisterUserUseCase subject, RegisterUserCommand command, Reason... expected) {
    assertThat(reasonsOf(() -> subject.register(command))).containsExactlyInAnyOrder(expected);
    assertThat(users.count()).isZero();
    assertThat(hasher.hashed()).isEmpty();
    assertThat(tokens.issues()).isEmpty();
  }

  @Test
  void anEmptyBodyReportsNameEmailAndPasswordRequiredTogether() {
    assertRejectedWith(
        command(null, null, null, null),
        Reason.NAME_REQUIRED,
        Reason.EMAIL_REQUIRED,
        Reason.PASSWORD_REQUIRED);
  }

  @Test
  void blankValuesGiveTheRequiredReasonOnlyForThatField() {
    assertRejectedWith(
        command("   ", "juan@rodriguez.org", "hunter2", List.of()), Reason.NAME_REQUIRED);
    assertRejectedWith(command("Juan", "", "hunter2", List.of()), Reason.EMAIL_REQUIRED);
    assertRejectedWith(
        command("Juan", "juan@rodriguez.org", "       ", List.of()), Reason.PASSWORD_REQUIRED);
  }

  @Test
  void mixedViolationsOfDifferentFieldsAreReportedTogether() {
    assertRejectedWith(
        command("   ", "juan", "hunter2", List.of()), Reason.NAME_REQUIRED, Reason.EMAIL_FORMAT);
  }

  @Test
  void everyFieldBreakingItsRuleIsReportedInOneRejection() {
    assertRejectedWith(
        command("", "juan@dominio", "abc12", Arrays.asList(new PhoneData[11])),
        Reason.NAME_REQUIRED,
        Reason.EMAIL_FORMAT,
        Reason.PASSWORD_FORMAT,
        Reason.PHONES_TOO_MANY);
  }

  @Test
  void aNameOfTwoHundredFiftyFiveCharactersIsRegistered() {
    User user =
        useCase.register(command("n".repeat(255), "juan@rodriguez.org", "hunter2", List.of()));

    assertThat(user.name()).hasSize(255);
  }

  @Test
  void aNameOfTwoHundredFiftySixCharactersIsRejected() {
    assertRejectedWith(
        command("n".repeat(256), "juan@rodriguez.org", "hunter2", List.of()), Reason.NAME_TOO_LONG);
  }

  @ParameterizedTest
  @NullAndEmptySource
  void absentNullOrEmptyPhonesStillRegisterTheUserWithNoPhones(List<PhoneData> phones) {
    User user = useCase.register(withPhones(phones));

    assertThat(user.phones()).isEmpty();
    assertThat(users.count()).isEqualTo(1);
  }

  @Test
  void aPhoneWithNoFieldsReportsThreeReasons() {
    assertRejectedWith(
        withPhones(List.of(phone(null, null, null))),
        Reason.PHONE_NUMBER_REQUIRED,
        Reason.CITY_CODE_REQUIRED,
        Reason.COUNTRY_CODE_REQUIRED);
  }

  @Test
  void oneMissingFieldInAPhoneGivesOnlyItsOwnReason() {
    assertRejectedWith(
        withPhones(List.of(phone("1234567", "1", null))), Reason.COUNTRY_CODE_REQUIRED);
    assertRejectedWith(
        withPhones(List.of(phone("1234567", null, "57"))), Reason.CITY_CODE_REQUIRED);
    assertRejectedWith(withPhones(List.of(phone(null, "1", "57"))), Reason.PHONE_NUMBER_REQUIRED);
  }

  @Test
  void theSameViolationInTwoPhonesCollapsesIntoOneReason() {
    RegisterUserCommand command =
        withPhones(List.of(phone(null, "1", "57"), phone(" ", "2", "56")));

    assertThat(reasonsOf(() -> useCase.register(command)))
        .hasSize(1)
        .containsExactly(Reason.PHONE_NUMBER_REQUIRED);
  }

  @Test
  void aPhoneFieldOverItsLimitGivesItsTooLongReason() {
    assertRejectedWith(
        withPhones(List.of(phone("9".repeat(5_000), "1", "57"))), Reason.PHONE_NUMBER_TOO_LONG);
    assertRejectedWith(
        withPhones(List.of(phone("1234567", "1".repeat(11), "57"))), Reason.CITY_CODE_TOO_LONG);
    assertRejectedWith(
        withPhones(List.of(phone("1234567", "1", "5".repeat(11)))), Reason.COUNTRY_CODE_TOO_LONG);
  }

  @Test
  void aPhoneFieldThatIsNotMadeOfDigitsGivesItsFormatReason() {
    assertRejectedWith(withPhones(List.of(phone("12-34", "1", "57"))), Reason.PHONE_NUMBER_FORMAT);
    assertRejectedWith(withPhones(List.of(phone("1234567", "a", "57"))), Reason.CITY_CODE_FORMAT);
    assertRejectedWith(
        withPhones(List.of(phone("1234567", "1", "5+7"))), Reason.COUNTRY_CODE_FORMAT);
  }

  @Test
  void aLeadingPlusIsAcceptedOnTheCountryCode() {
    User user = useCase.register(withPhones(List.of(phone("1234567", "1", "+57"))));

    assertThat(user.phones()).containsExactly(new Phone("1234567", "1", "+57"));
  }

  @Test
  void aLeadingPlusIsRejectedOnTheNumber() {
    assertRejectedWith(
        withPhones(List.of(phone("+1234567", "1", "57"))), Reason.PHONE_NUMBER_FORMAT);
  }

  @Test
  void aNullEntryInThePhoneListIsRejectedAsNull() {
    assertRejectedWith(withPhones(Arrays.asList((PhoneData) null)), Reason.PHONE_NULL);
    assertRejectedWith(
        withPhones(Arrays.asList(phone("1234567", "1", "57"), null, phone(null, "1", "57"))),
        Reason.PHONE_NULL,
        Reason.PHONE_NUMBER_REQUIRED);
  }

  @Test
  void tenPhonesAreRegisteredInOrder() {
    List<PhoneData> tenPhones =
        Stream.of("0", "1", "2", "3", "4", "5", "6", "7", "8", "9")
            .map(digit -> phone("100000" + digit, "1", "57"))
            .toList();

    User user = useCase.register(withPhones(tenPhones));

    assertThat(user.phones())
        .extracting(Phone::number)
        .containsExactly(
            "1000000", "1000001", "1000002", "1000003", "1000004", "1000005", "1000006", "1000007",
            "1000008", "1000009");
  }

  @Test
  void elevenPhonesAreTooManyAndTheirEntriesAreNotInspected() {
    List<PhoneData> elevenNulls = Arrays.asList(new PhoneData[11]);
    List<PhoneData> elevenInvalid =
        Stream.generate(() -> phone(null, null, null)).limit(11).toList();

    assertRejectedWith(withPhones(elevenNulls), Reason.PHONES_TOO_MANY);
    assertRejectedWith(withPhones(elevenInvalid), Reason.PHONES_TOO_MANY);
  }

  @Test
  void aPasswordOfSeventyTwoCharactersIsAcceptedWhateverThePattern() {
    User user =
        permissivePasswordUseCase()
            .register(command("Juan", "juan@rodriguez.org", "a".repeat(72), List.of()));

    assertThat(user.passwordHash()).isEqualTo(FakePasswordHasher.expectedHashOf("a".repeat(72)));
  }

  @Test
  void aPasswordOverSeventyTwoBytesIsTooLongEvenWithAPermissivePattern() {
    RegisterUserUseCase permissive = permissivePasswordUseCase();

    assertRejectedWith(
        permissive,
        command("Juan", "juan@rodriguez.org", "a".repeat(73), List.of()),
        Reason.PASSWORD_TOO_LONG);
    assertRejectedWith(
        permissive,
        command("Juan", "juan@rodriguez.org", "é".repeat(40), List.of()),
        Reason.PASSWORD_TOO_LONG);
  }

  @Test
  void aHundredThousandCharacterPasswordIsRejectedBeforeTheFormatRuleRuns() {
    PasswordPolicy mustNotRun =
        rawPassword -> {
          throw new AssertionError("the format rule must not see an oversized password");
        };
    var subject =
        new RegisterUserUseCase(
            users,
            hasher,
            tokens,
            mustNotRun,
            EMAIL_FORMAT,
            Clock.fixed(CLOCK_INSTANT, ZoneOffset.UTC));

    assertTimeoutPreemptively(
        Duration.ofSeconds(5),
        () ->
            assertRejectedWith(
                subject,
                command("Juan", "juan@rodriguez.org", "a".repeat(100_000), List.of()),
                Reason.PASSWORD_TOO_LONG));
  }

  @Test
  void aFiftyThousandCharacterEmailIsRejectedByLengthBeforeTheFormatRuns() {
    Pattern catastrophic = Pattern.compile("^(a+)+$");
    var subject =
        new RegisterUserUseCase(
            users,
            hasher,
            tokens,
            PASSWORD_POLICY,
            catastrophic,
            Clock.fixed(CLOCK_INSTANT, ZoneOffset.UTC));

    assertTimeoutPreemptively(
        Duration.ofSeconds(5),
        () ->
            assertRejectedWith(
                subject,
                command("Juan", "a".repeat(50_000) + "@x", "hunter2", List.of()),
                Reason.EMAIL_TOO_LONG));
  }

  @Test
  void aRejectedRegistrationNeverHashesIssuesATokenOrSaves() {
    assertRejectedWith(
        command("   ", "juan@rodriguez.org", "hunter2", List.of()), Reason.NAME_REQUIRED);
  }

  @Test
  void aRejectedRegistrationOnAnExistingEmailKeepsTheStoredUserUntouched() {
    User first = useCase.register(statementCommand());

    assertThat(
            reasonsOf(
                () -> useCase.register(command("", "juan@rodriguez.org", "hunter2", List.of()))))
        .containsExactly(Reason.NAME_REQUIRED);

    assertThat(users.saved()).containsExactly(first);
    assertThat(hasher.hashed()).hasSize(1);
  }

  private RegisterUserUseCase permissivePasswordUseCase() {
    return new RegisterUserUseCase(
        users,
        hasher,
        tokens,
        new RegexPasswordPolicy(Pattern.compile("^.+$")),
        EMAIL_FORMAT,
        Clock.fixed(CLOCK_INSTANT, ZoneOffset.UTC));
  }

  /**
   * Un reloj que avanza en cada lectura, de modo que una segunda lectura se vea como un valor
   * distinto.
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
