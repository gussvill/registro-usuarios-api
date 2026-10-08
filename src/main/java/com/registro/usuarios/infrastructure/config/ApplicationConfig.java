package com.registro.usuarios.infrastructure.config;

import com.registro.usuarios.application.RegisterUserUseCase;
import com.registro.usuarios.application.port.RegisterUser;
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
 * Configura la capa de aplicación. El caso de uso y la política de contraseña no llevan anotación
 * de estereotipo, así que en esta clase se convierten en beans. Un patrón que no sea una expresión
 * regular válida detiene el arranque con un mensaje que nombra la propiedad.
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
  RegisterUser registerUser(
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
