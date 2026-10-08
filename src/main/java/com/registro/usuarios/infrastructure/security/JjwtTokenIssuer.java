package com.registro.usuarios.infrastructure.security;

import com.registro.usuarios.domain.model.Email;
import com.registro.usuarios.domain.model.UserId;
import com.registro.usuarios.domain.port.TokenIssuer;
import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Implementa el puerto {@link TokenIssuer} con JJWT: un JWT HS256 cuyo subject es el id del usuario
 * y cuyo claim {@code email} es la dirección normalizada.
 *
 * <p>Cuando hay un secreto configurado, la clave son sus bytes en bruto. Un secreto de menos de 32
 * bytes se rechaza al crear el emisor, de modo que la aplicación falla al arrancar en lugar de
 * responder con un error a cada registro. El mensaje nombra la propiedad y nunca el valor. Cuando
 * no hay secreto configurado (está ausente o vacío) se genera una clave aleatoria de 256 bits al
 * arrancar, una línea INFO lo indica y la clave nunca se registra en el log: los tokens que firma
 * no sobreviven a un reinicio. Ninguna aplicación se entrega con un secreto utilizable. La
 * expiración debe ser una duración positiva; de lo contrario, cada token nacería expirado.
 */
@Component
class JjwtTokenIssuer implements TokenIssuer {

  private static final Logger LOG = LoggerFactory.getLogger(JjwtTokenIssuer.class);

  static final int MIN_SECRET_BYTES = 32;
  private static final String SECRET_PROPERTY = "app.token.secret";
  private static final String EXPIRATION_PROPERTY = "app.token.expiration";

  private final SecretKey key;
  private final TokenProperties properties;

  @Autowired
  JjwtTokenIssuer(TokenProperties properties) {
    this(properties, new SecureRandom());
  }

  JjwtTokenIssuer(TokenProperties properties, SecureRandom random) {
    this.properties = properties;
    requirePositive(properties.expiration());
    this.key =
        isConfigured(properties.secret()) ? keyOf(properties.secret()) : ephemeralKey(random);
  }

  private static boolean isConfigured(String secret) {
    return secret != null && !secret.isEmpty();
  }

  private static SecretKey ephemeralKey(SecureRandom random) {
    byte[] bytes = new byte[MIN_SECRET_BYTES];
    random.nextBytes(bytes);
    LOG.info(
        "{} is not configured: an ephemeral signing key is in use and issued tokens will not"
            + " survive a restart",
        SECRET_PROPERTY);
    return new SecretKeySpec(bytes, "HmacSHA256");
  }

  private static void requirePositive(Duration expiration) {
    if (expiration == null || expiration.isZero() || expiration.isNegative()) {
      throw new IllegalStateException(EXPIRATION_PROPERTY + " must be a positive duration");
    }
  }

  private static SecretKey keyOf(String secret) {
    byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
    if (bytes.length < MIN_SECRET_BYTES) {
      throw new IllegalStateException(
          SECRET_PROPERTY + " must be at least " + MIN_SECRET_BYTES + " bytes");
    }
    return new SecretKeySpec(bytes, "HmacSHA256");
  }

  // El builder de JJWT recibe java.util.Date; se convierte desde un Instant en este único punto.
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
