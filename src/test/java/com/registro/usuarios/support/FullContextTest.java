package com.registro.usuarios.support;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

/**
 * Configuración compartida de toda prueba de contexto completo, para que todas reutilicen un único
 * contexto de aplicación en caché: un puerto aleatorio, una base de datos en memoria aislada, un
 * secreto y una expiración de token conocidos, y un reloj fijo.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@SpringBootTest(
    webEnvironment = RANDOM_PORT,
    properties = {
      "spring.datasource.url=jdbc:h2:mem:${random.uuid};DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
      "app.token.secret=" + FullContextTest.TOKEN_SECRET,
      "app.token.expiration=3600s"
    })
@Import(FullContextTest.FixedClockConfiguration.class)
public @interface FullContextTest {

  /**
   * Secreto de firma conocido (al menos 32 bytes) que usan las pruebas que verifican los tokens
   * emitidos.
   */
  String TOKEN_SECRET = "test-only-secret-0123456789-abcdefghijklmnop";

  /** El instante que informa el bean {@link Clock} dentro de las pruebas de contexto completo. */
  Instant FIXED_INSTANT = Instant.parse("2026-01-15T10:30:00Z");

  /** Reemplaza el reloj de producción por uno fijo. */
  @TestConfiguration(proxyBeanMethods = false)
  class FixedClockConfiguration {

    @Bean
    @Primary
    Clock fixedClock() {
      return Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
    }
  }
}
