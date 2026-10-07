package com.registro.usuarios.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.registro.usuarios.domain.model.Email;
import com.registro.usuarios.domain.model.UserId;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import java.util.regex.Pattern;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The issuer verified the way a consumer would: parse the token back with the shared secret. */
// JJWT's parser clock and claim accessors are expressed in java.util.Date.
@SuppressWarnings("JavaUtilDate")
class JjwtTokenIssuerTest {

  private static final String SECRET = "test-only-secret-0123456789-abcdefghijklmnop";
  private static final Pattern ANY = Pattern.compile("^.+$");
  private static final Instant ISSUED_AT = Instant.parse("2026-01-15T10:30:00.987654Z");
  private static final UserId SUBJECT =
      new UserId(UUID.fromString("0b9e3b0e-6a4c-4d52-9b8e-1f1f2d6d7a10"));
  private static final Email EMAIL = Email.of("juan@rodriguez.org", ANY);

  private static SecretKey keyOf(String secret) {
    return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
  }

  private static JjwtTokenIssuer issuer(String secret, Duration expiration) {
    return new JjwtTokenIssuer(new TokenProperties(secret, expiration));
  }

  /** Verifies the signature and judges expiry at the moment of issue, as a consumer would. */
  private static Jws<Claims> parse(String token, String secret, Instant now) {
    return Jwts.parser()
        .verifyWith(keyOf(secret))
        .clock(() -> Date.from(now))
        .build()
        .parseSignedClaims(token);
  }

  @Test
  void theTokenIsSignedWithHs256AndCarriesTheSubjectAndTheEmail() {
    String token = issuer(SECRET, Duration.ofHours(1)).issue(SUBJECT, EMAIL, ISSUED_AT);

    Jws<Claims> parsed = parse(token, SECRET, ISSUED_AT);

    assertThat(parsed.getHeader().getAlgorithm()).isEqualTo("HS256");
    assertThat(parsed.getPayload().getSubject()).isEqualTo("0b9e3b0e-6a4c-4d52-9b8e-1f1f2d6d7a10");
    assertThat(parsed.getPayload().get("email", String.class)).isEqualTo("juan@rodriguez.org");
  }

  @Test
  void otherInputsGiveOtherClaims() {
    UserId otherSubject = new UserId(UUID.fromString("11111111-2222-3333-4444-555555555555"));
    Email otherEmail = Email.of("MARIA@Dominio.CL", ANY);

    String token = issuer(SECRET, Duration.ofMinutes(1)).issue(otherSubject, otherEmail, ISSUED_AT);

    Claims claims = parse(token, SECRET, ISSUED_AT).getPayload();
    assertThat(claims.getSubject()).isEqualTo("11111111-2222-3333-4444-555555555555");
    assertThat(claims.get("email", String.class)).isEqualTo("maria@dominio.cl");
  }

  @Test
  void issuedAtIsTheCreatedInstantAtSecondPrecision() {
    String token = issuer(SECRET, Duration.ofHours(1)).issue(SUBJECT, EMAIL, ISSUED_AT);

    Claims claims = parse(token, SECRET, ISSUED_AT).getPayload();

    assertThat(claims.getIssuedAt().toInstant().getEpochSecond())
        .isEqualTo(ISSUED_AT.getEpochSecond())
        .isEqualTo(1_768_473_000L);
    assertThat(claims.getIssuedAt().toInstant()).isEqualTo(Instant.parse("2026-01-15T10:30:00Z"));
  }

  @ParameterizedTest
  @ValueSource(longs = {120, 3600, 900})
  void expirationIsIssuedAtPlusTheConfiguredDuration(long seconds) {
    String token = issuer(SECRET, Duration.ofSeconds(seconds)).issue(SUBJECT, EMAIL, ISSUED_AT);

    Claims claims = parse(token, SECRET, ISSUED_AT).getPayload();

    long issuedAt = claims.getIssuedAt().toInstant().getEpochSecond();
    long expiresAt = claims.getExpiration().toInstant().getEpochSecond();
    assertThat(expiresAt - issuedAt).isEqualTo(seconds);
  }

  @Test
  void aTokenIsNoLongerAcceptedOnceItExpires() {
    String token = issuer(SECRET, Duration.ofMinutes(2)).issue(SUBJECT, EMAIL, ISSUED_AT);

    assertThat(parse(token, SECRET, ISSUED_AT.plusSeconds(60)).getPayload().getSubject())
        .isEqualTo(SUBJECT.value().toString());
    assertThatThrownBy(() -> parse(token, SECRET, ISSUED_AT.plusSeconds(180)))
        .isInstanceOf(io.jsonwebtoken.ExpiredJwtException.class);
  }

  @Test
  void aTokenDoesNotVerifyWithAnotherSecret() {
    String token = issuer(SECRET, Duration.ofHours(1)).issue(SUBJECT, EMAIL, ISSUED_AT);

    assertThatThrownBy(() -> parse(token, "another-secret-0123456789-abcdefghijklmnopq", ISSUED_AT))
        .isInstanceOf(SignatureException.class);
  }

  @Test
  void aSecretOf31BytesIsRejectedAndTheMessageNamesThePropertyButNotTheValue() {
    String secret = "Zq9-unique-31-bytes-secret-vlue";
    assertThat(secret.getBytes(StandardCharsets.UTF_8)).hasSize(31);

    assertThatThrownBy(() -> issuer(secret, Duration.ofMinutes(1)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("app.token.secret")
        .hasMessageContaining("32 bytes")
        .hasMessageNotContaining(secret)
        .hasMessageNotContaining("Zq9");
  }

  @Test
  void aSecretOf32BytesIsAccepted() {
    String secret = "x".repeat(32);

    String token = issuer(secret, Duration.ofMinutes(1)).issue(SUBJECT, EMAIL, ISSUED_AT);

    assertThat(parse(token, secret, ISSUED_AT).getPayload().getSubject())
        .isEqualTo(SUBJECT.value().toString());
  }

  @Test
  void theLengthOfTheSecretIsMeasuredInBytesNotCharacters() {
    String sixteenCharsThirtyTwoBytes = "é".repeat(16);
    String fifteenCharsThirtyBytes = "é".repeat(15);

    assertThat(issuer(sixteenCharsThirtyTwoBytes, Duration.ofMinutes(1))).isNotNull();
    assertThatThrownBy(() -> issuer(fifteenCharsThirtyBytes, Duration.ofMinutes(1)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("app.token.secret");
  }

  @Test
  void aMissingSecretIsRejectedLikeAShortOne() {
    assertThatThrownBy(() -> issuer(null, Duration.ofMinutes(1)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("app.token.secret");
  }

  @ParameterizedTest
  @ValueSource(longs = {0, -1, -3600})
  void aZeroOrNegativeExpirationIsRejectedAndTheMessageNamesTheProperty(long seconds) {
    assertThatThrownBy(() -> issuer(SECRET, Duration.ofSeconds(seconds)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("app.token.expiration")
        .hasMessageContaining("positive");
  }

  @Test
  void aMissingExpirationIsRejectedLikeANonPositiveOne() {
    assertThatThrownBy(() -> issuer(SECRET, null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("app.token.expiration");
  }

  @Test
  void anExpirationOfOneSecondIsAccepted() {
    String token = issuer(SECRET, Duration.ofSeconds(1)).issue(SUBJECT, EMAIL, ISSUED_AT);

    Claims claims = parse(token, SECRET, ISSUED_AT).getPayload();
    assertThat(claims.getExpiration().getTime() - claims.getIssuedAt().getTime()).isEqualTo(1000L);
  }

  @Test
  void aTokenForTheLongestEmailFitsTheTokenColumn() {
    Email longest =
        Email.of("a".repeat(Email.MAX_LENGTH - "@dominio.cl".length()) + "@dominio.cl", ANY);
    assertThat(longest.value()).hasSize(254);

    String token = issuer(SECRET, Duration.ofHours(1)).issue(SUBJECT, longest, ISSUED_AT);

    assertThat(token.length()).isLessThanOrEqualTo(1024);
    assertThat(parse(token, SECRET, ISSUED_AT).getPayload().get("email", String.class))
        .isEqualTo(longest.value());
  }
}
