package com.registro.usuarios.infrastructure.security;

import com.registro.usuarios.domain.model.Email;
import com.registro.usuarios.domain.model.UserId;
import com.registro.usuarios.domain.port.TokenIssuer;
import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Implements the {@link TokenIssuer} port with JJWT: an HS256 JWT whose subject is the user id and
 * whose {@code email} claim is the normalised address.
 *
 * <p>The key is the raw bytes of the configured secret. A secret shorter than 32 bytes is refused
 * when the issuer is created, so the application fails to start instead of answering every
 * registration with an error. The message names the property and never the value. The expiration
 * must be a positive duration, otherwise every token would be born expired.
 */
@Component
class JjwtTokenIssuer implements TokenIssuer {

  static final int MIN_SECRET_BYTES = 32;
  private static final String SECRET_PROPERTY = "app.token.secret";
  private static final String EXPIRATION_PROPERTY = "app.token.expiration";

  private final SecretKey key;
  private final TokenProperties properties;

  JjwtTokenIssuer(TokenProperties properties) {
    this.properties = properties;
    this.key = keyOf(properties.secret());
    requirePositive(properties.expiration());
  }

  private static void requirePositive(Duration expiration) {
    if (expiration == null || expiration.isZero() || expiration.isNegative()) {
      throw new IllegalStateException(EXPIRATION_PROPERTY + " must be a positive duration");
    }
  }

  private static SecretKey keyOf(String secret) {
    byte[] bytes = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
    if (bytes.length < MIN_SECRET_BYTES) {
      throw new IllegalStateException(
          SECRET_PROPERTY + " must be at least " + MIN_SECRET_BYTES + " bytes");
    }
    return new SecretKeySpec(bytes, "HmacSHA256");
  }

  // JJWT's builder takes java.util.Date; it is converted from an Instant at this single point.
  @SuppressWarnings("JavaUtilDate")
  @Override
  public String issue(UserId subject, Email email, Instant issuedAt) {
    Instant issuedAtSecond = issuedAt.truncatedTo(ChronoUnit.SECONDS);
    return Jwts.builder()
        .subject(subject.value().toString())
        .claim("email", email.value())
        .issuedAt(Date.from(issuedAtSecond))
        .expiration(Date.from(issuedAtSecond.plus(properties.expiration())))
        .signWith(key, Jwts.SIG.HS256)
        .compact();
  }
}
