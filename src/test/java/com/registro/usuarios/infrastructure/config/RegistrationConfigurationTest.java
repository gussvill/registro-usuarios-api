package com.registro.usuarios.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.registro.usuarios.application.port.RegisterUser;
import com.registro.usuarios.application.port.RegisterUserCommand;
import com.registro.usuarios.application.port.RegisterUserCommand.PhoneData;
import com.registro.usuarios.domain.exception.InvalidUserDataException;
import com.registro.usuarios.domain.exception.Reason;
import com.registro.usuarios.domain.model.Email;
import com.registro.usuarios.domain.model.User;
import com.registro.usuarios.domain.policy.PasswordPolicy;
import com.registro.usuarios.infrastructure.security.TokenProperties;
import com.registro.usuarios.support.InMemoryUserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.swagger.v3.oas.models.OpenAPI;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * El cableado sin servidor web ni base de datos: las clases de configuración reales, el emisor de
 * tokens y el hasher reales encontrados al escanear su paquete, y un repositorio en memoria.
 */
@SuppressWarnings("JavaUtilDate") // El reloj del parser de JJWT es un proveedor de java.util.Date.
class RegistrationConfigurationTest {

  private static final String SECRET = "test-only-secret-0123456789-abcdefghijklmnop";

  /** Todo lo que la aplicación cablea, menos la web y la base de datos. */
  @Configuration(proxyBeanMethods = false)
  @Import({ApplicationConfig.class, OpenApiConfig.class})
  @EnableConfigurationProperties({RegistrationProperties.class, TokenProperties.class})
  @ComponentScan(basePackageClasses = TokenProperties.class)
  static class Wiring {

    @Bean
    InMemoryUserRepository users() {
      return new InMemoryUserRepository();
    }
  }

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner().withUserConfiguration(Wiring.class);

  private ApplicationContextRunner withDefaults() {
    return runner.withPropertyValues(
        "app.registration.email-pattern=^[a-z]+@[a-z]+\\.[a-z]{2,}$",
        "app.registration.password-pattern=^.{4,}$",
        "app.token.secret=" + SECRET,
        "app.token.expiration=3600s");
  }

  private static RegisterUserCommand command(String email, String password) {
    return new RegisterUserCommand(
        "Juan Rodriguez", email, password, List.of(new PhoneData("1234567", "1", "57")));
  }

  private static List<Reason> reasonsOfRejection(RegisterUser useCase, RegisterUserCommand c) {
    try {
      useCase.register(c);
    } catch (InvalidUserDataException rejected) {
      return new ArrayList<>(rejected.reasons());
    }
    return List.of();
  }

  private static List<String> messagesOf(Throwable failure) {
    List<String> messages = new ArrayList<>();
    for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
      messages.add(String.valueOf(cause.getMessage()));
    }
    return messages;
  }

  // --- enlace de las cuatro claves ---

  @Test
  void theFourPropertyKeysBindToTheirTypedHolders() {
    runner
        .withPropertyValues(
            "app.registration.email-pattern=^e$",
            "app.registration.password-pattern=^p$",
            "app.token.secret=" + SECRET,
            "app.token.expiration=120s")
        .run(
            context -> {
              RegistrationProperties registration = context.getBean(RegistrationProperties.class);
              TokenProperties token = context.getBean(TokenProperties.class);
              assertThat(registration.emailPattern()).isEqualTo("^e$");
              assertThat(registration.passwordPattern()).isEqualTo("^p$");
              assertThat(token.secret()).isEqualTo(SECRET);
              assertThat(token.expiration()).isEqualTo(Duration.ofMinutes(2));
            });
  }

  @Test
  void aDifferentSetOfValuesBindsToDifferentValues() {
    runner
        .withPropertyValues(
            "app.registration.email-pattern=^other-email$",
            "app.registration.password-pattern=^other-password$",
            "app.token.secret=" + "z".repeat(40),
            "app.token.expiration=15m")
        .run(
            context -> {
              assertThat(context.getBean(RegistrationProperties.class).emailPattern())
                  .isEqualTo("^other-email$");
              assertThat(context.getBean(RegistrationProperties.class).passwordPattern())
                  .isEqualTo("^other-password$");
              assertThat(context.getBean(TokenProperties.class).secret()).isEqualTo("z".repeat(40));
              assertThat(context.getBean(TokenProperties.class).expiration())
                  .isEqualTo(Duration.ofMinutes(15));
            });
  }

  // --- los patrones son configuración ---

  @Test
  void theEmailPatternPropertyChangesWhichAddressesAreAccepted() {
    runner
        .withPropertyValues(
            "app.registration.email-pattern=^[a-z]+@dominio\\.cl$",
            "app.registration.password-pattern=^.+$",
            "app.token.secret=" + SECRET,
            "app.token.expiration=3600s")
        .run(
            context -> {
              RegisterUser useCase = context.getBean(RegisterUser.class);

              assertThat(reasonsOfRejection(useCase, command("juan@rodriguez.org", "hunter2")))
                  .containsExactly(Reason.EMAIL_FORMAT);
              assertThat(useCase.register(command("juan@dominio.cl", "hunter2")).email().value())
                  .isEqualTo("juan@dominio.cl");
            });
  }

  @Test
  void thePasswordPatternPropertyChangesWhichPasswordsAreAccepted() {
    runner
        .withPropertyValues(
            "app.registration.email-pattern=^.+$",
            "app.registration.password-pattern=^[A-Z]{8,}$",
            "app.token.secret=" + SECRET,
            "app.token.expiration=3600s")
        .run(
            context -> {
              RegisterUser useCase = context.getBean(RegisterUser.class);

              assertThat(reasonsOfRejection(useCase, command("juan@dominio.cl", "hunter2")))
                  .containsExactly(Reason.PASSWORD_FORMAT);
              assertThat(useCase.register(command("juan@dominio.cl", "ABCDEFGH")).active())
                  .isTrue();
            });
  }

  @Test
  void thePasswordPolicyBeanFollowsTheProperty() {
    withDefaults()
        .run(
            context -> {
              PasswordPolicy policy = context.getBean(PasswordPolicy.class);
              assertThat(policy.isSatisfiedBy("abcd")).isTrue();
              assertThat(policy.isSatisfiedBy("abc")).isFalse();
            });
  }

  // --- la configuración del token llega al token emitido ---

  @ParameterizedTest
  @CsvSource({"120s, 120", "3600s, 3600", "15m, 900"})
  void theExpirationPropertyDecidesTheDistanceBetweenIssueAndExpiry(
      String expiration, long seconds) {
    withDefaults()
        .withPropertyValues("app.token.expiration=" + expiration)
        .run(
            context -> {
              User user =
                  context
                      .getBean(RegisterUser.class)
                      .register(command("juan@dominio.cl", "hunter2"));

              Claims claims =
                  Jwts.parser()
                      .verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                      .clock(() -> Date.from(user.created()))
                      .build()
                      .parseSignedClaims(user.token())
                      .getPayload();
              assertThat(
                      claims.getExpiration().getTime() / 1000
                          - claims.getIssuedAt().getTime() / 1000)
                  .isEqualTo(seconds);
            });
  }

  @Test
  void theUseCaseUsesTheSystemClockOfTheApplication() {
    withDefaults()
        .run(
            context -> {
              assertThat(context.getBean(Clock.class).getZone().getId()).isEqualTo("Z");
              User user =
                  context
                      .getBean(RegisterUser.class)
                      .register(command("juan@dominio.cl", "hunter2"));
              assertThat(Duration.between(user.created(), context.getBean(Clock.class).instant()))
                  .isLessThan(Duration.ofSeconds(30));
            });
  }

  // --- fallar rápido ---

  @Test
  void aSecretOf16BytesStopsTheStartupAndTheFailureNamesThePropertyButNotTheValue() {
    String weak = "0123456789abcdef";
    assertThat(weak.getBytes(StandardCharsets.UTF_8)).hasSize(16);

    withDefaults()
        .withPropertyValues("app.token.secret=" + weak)
        .run(
            context -> {
              assertThat(context).hasFailed();
              List<String> messages = messagesOf(context.getStartupFailure());
              assertThat(messages).anySatisfy(m -> assertThat(m).contains("app.token.secret"));
              assertThat(messages).noneSatisfy(m -> assertThat(m).contains(weak));
            });
  }

  @Test
  void aMissingSecretStartsTheApplicationWithAnEphemeralKey() {
    runner
        .withPropertyValues(
            "app.registration.email-pattern=^.+$",
            "app.registration.password-pattern=^.+$",
            "app.token.expiration=3600s")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              User user =
                  context
                      .getBean(RegisterUser.class)
                      .register(command("juan@dominio.cl", "hunter2"));
              assertThat(user.token()).matches("[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+");
            });
  }

  @Test
  void anEmptySecretIsTheSameAsNoSecret() {
    withDefaults()
        .withPropertyValues("app.token.secret=")
        .run(context -> assertThat(context).hasNotFailed());
  }

  @Test
  void aMissingExpirationStopsTheStartupAndTheFailureNamesTheProperty() {
    runner
        .withPropertyValues(
            "app.registration.email-pattern=^.+$",
            "app.registration.password-pattern=^.+$",
            "app.token.secret=" + SECRET)
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(messagesOf(context.getStartupFailure()))
                  .anySatisfy(m -> assertThat(m).contains("app.token.expiration"));
            });
  }

  @ParameterizedTest
  @CsvSource({"0s", "-5s", "0m"})
  void aZeroOrNegativeExpirationStopsTheStartupAndTheFailureNamesTheProperty(String expiration) {
    withDefaults()
        .withPropertyValues("app.token.expiration=" + expiration)
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(messagesOf(context.getStartupFailure()))
                  .anySatisfy(m -> assertThat(m).contains("app.token.expiration"));
            });
  }

  @Test
  void aSecretOf32BytesStartsTheApplication() {
    withDefaults()
        .withPropertyValues("app.token.secret=" + "k".repeat(32))
        .run(context -> assertThat(context).hasNotFailed());
  }

  @Test
  void anInvalidEmailPatternStopsTheStartupAndTheFailureNamesTheProperty() {
    withDefaults()
        .withPropertyValues("app.registration.email-pattern=[unclosed")
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(messagesOf(context.getStartupFailure()))
                  .anySatisfy(m -> assertThat(m).contains("app.registration.email-pattern"));
            });
  }

  @Test
  void anInvalidPasswordPatternStopsTheStartupAndTheFailureNamesTheProperty() {
    withDefaults()
        .withPropertyValues("app.registration.password-pattern=(unclosed")
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(messagesOf(context.getStartupFailure()))
                  .anySatisfy(m -> assertThat(m).contains("app.registration.password-pattern"));
            });
  }

  @Test
  void aBlankPatternStopsTheStartup() {
    withDefaults()
        .withPropertyValues("app.registration.email-pattern= ")
        .run(context -> assertThat(context).hasFailed());
  }

  // --- el bean del caso de uso y el bean de OpenAPI ---

  @Test
  void theOpenApiBeanCarriesTheTitleAndTheVersionOfTheApi() {
    withDefaults()
        .run(
            context -> {
              OpenAPI openApi = context.getBean(OpenAPI.class);
              assertThat(openApi.getInfo().getTitle()).isEqualTo("API de registro de usuarios");
              assertThat(openApi.getInfo().getVersion()).isEqualTo("1.0.0");
              assertThat(openApi.getInfo().getDescription()).isNotBlank();
            });
  }

  // --- los valores por defecto con los que se entrega la aplicación ---

  private ApplicationContextRunner shipped() {
    return new ApplicationContextRunner()
        .withUserConfiguration(Wiring.class)
        .withInitializer(new ConfigDataApplicationContextInitializer());
  }

  @Test
  void theApplicationStartsWithNothingButItsShippedDefaults() {
    shipped()
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context.getBean(TokenProperties.class).expiration())
                  .isEqualTo(Duration.ofMinutes(15));
              assertThat(context.getBean(TokenProperties.class).secret()).isNullOrEmpty();
              assertThat(context.getEnvironment().getProperty("spring.mvc.log-resolved-exception"))
                  .isEqualTo("false");
            });
  }

  @Test
  void theDefaultEmailPatternAcceptsTheDocumentedAddresses() {
    shipped()
        .run(
            context -> {
              Pattern pattern =
                  Pattern.compile(context.getBean(RegistrationProperties.class).emailPattern());
              assertThat(
                      Stream.of(
                              "aaaaaaa@dominio.cl", "juan@rodriguez.org", "a.b+c@sub.dominio.co.uk")
                          .map(email -> Email.violation(email, pattern).isPresent()))
                  .containsExactly(false, false, false);
            });
  }

  @Test
  void theDefaultEmailPatternRejectsTheDocumentedMalformedAddresses() {
    shipped()
        .run(
            context -> {
              Pattern pattern =
                  Pattern.compile(context.getBean(RegistrationProperties.class).emailPattern());
              List<String> rejected =
                  Stream.of(
                          "juan",
                          "juan@",
                          "@dominio.cl",
                          "juan@dominio",
                          "juan@@dominio.cl",
                          " juan@dominio.cl",
                          "juan@rodriguez.org\n")
                      .filter(email -> Email.violation(email, pattern).isPresent())
                      .toList();
              assertThat(rejected).hasSize(7);
            });
  }

  @Test
  void theDefaultPasswordPatternAcceptsTheStatementPasswordAndRejectsAShortOne() {
    shipped()
        .run(
            context -> {
              PasswordPolicy policy = context.getBean(PasswordPolicy.class);
              assertThat(policy.isSatisfiedBy("hunter2")).isTrue();
              assertThat(policy.isSatisfiedBy("abc12")).isFalse();
            });
  }

  @Test
  void theShippedPropertiesTakeTheSecretFromTheEnvironmentAndCarryNoValueForIt() throws Exception {
    List<String> lines;
    try (InputStream properties = getClass().getResourceAsStream("/application.properties")) {
      assertThat(properties).as("application.properties en el classpath").isNotNull();
      lines = new String(properties.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
    }

    assertThat(lines).contains("app.token.secret=${TOKEN_SECRET:}");
  }
}
