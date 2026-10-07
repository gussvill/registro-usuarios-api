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
 * Shared configuration of every full-context test, so that they all reuse one cached application
 * context: a random port, an isolated in-memory database, a known token secret and expiration, and
 * a fixed clock.
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

  /** Known signing secret (at least 32 bytes) used by tests that verify issued tokens. */
  String TOKEN_SECRET = "test-only-secret-0123456789-abcdefghijklmnop";

  /** The instant reported by the {@link Clock} bean inside full-context tests. */
  Instant FIXED_INSTANT = Instant.parse("2026-01-15T10:30:00Z");

  /** Replaces the production clock with a fixed one. */
  @TestConfiguration(proxyBeanMethods = false)
  class FixedClockConfiguration {

    @Bean
    @Primary
    Clock fixedClock() {
      return Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
    }
  }
}
