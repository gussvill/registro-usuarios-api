package com.registro.usuarios.infrastructure.config;

import com.registro.usuarios.application.RegisterUserUseCase;
import com.registro.usuarios.domain.policy.PasswordPolicy;
import com.registro.usuarios.domain.policy.RegexPasswordPolicy;
import com.registro.usuarios.domain.port.PasswordHasher;
import com.registro.usuarios.domain.port.TokenIssuer;
import com.registro.usuarios.domain.port.UserRepository;
import java.time.Clock;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the application layer. The use case and the password policy carry no stereotype annotation,
 * so this class is where they become beans. A pattern that is not a valid regular expression stops
 * the startup with a message that names the property.
 */
@Configuration(proxyBeanMethods = false)
class ApplicationConfig {

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  PasswordPolicy passwordPolicy(RegistrationProperties properties) {
    return new RegexPasswordPolicy(
        compile(properties.passwordPattern(), "app.registration.password-pattern"));
  }

  @Bean
  RegisterUserUseCase registerUserUseCase(
      UserRepository users,
      PasswordHasher hasher,
      TokenIssuer tokens,
      PasswordPolicy passwordPolicy,
      RegistrationProperties properties,
      Clock clock) {
    return new RegisterUserUseCase(
        users,
        hasher,
        tokens,
        passwordPolicy,
        compile(properties.emailPattern(), "app.registration.email-pattern"),
        clock);
  }

  private static Pattern compile(String regex, String property) {
    try {
      return Pattern.compile(regex);
    } catch (PatternSyntaxException e) {
      throw new IllegalStateException(property + " is not a valid regular expression", e);
    }
  }
}
