package com.registro.usuarios.domain.model;

import static com.registro.usuarios.support.Rejections.reasonsOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.registro.usuarios.domain.exception.Reason;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class UserTest {

  private static final Pattern ANY_EMAIL = Pattern.compile("^.+@.+$");
  private static final Instant NOW = Instant.parse("2026-01-15T10:30:00.123456Z");
  private static final UserId ID =
      new UserId(UUID.fromString("0b9e3b0e-6a4c-4d52-9b8e-1f1f2d6d7a10"));
  private static final String HASH = "$2a$12$abcdefghijklmnopqrstuuABCDEFGHIJKLMNOPQRSTUVWXYZ01234";
  private static final String TOKEN = "header.payload.signature";

  private static User.Builder validBuilder() {
    return User.registration()
        .id(ID)
        .name("Juan Rodriguez")
        .email(Email.of("juan@rodriguez.org", ANY_EMAIL))
        .passwordHash(HASH)
        .phones(List.of(new Phone("1234567", "1", "57")))
        .token(TOKEN)
        .registeredAt(NOW);
  }

  @Test
  void registrationSetsTheThreeTimestampsToTheSameInstantAndActivatesTheUser() {
    User user = validBuilder().build();

    assertThat(user.created()).isEqualTo(NOW);
    assertThat(user.modified()).isEqualTo(NOW);
    assertThat(user.lastLogin()).isEqualTo(NOW);
    assertThat(user.active()).isTrue();
  }

  @Test
  void registrationTimestampsFollowTheSuppliedInstant() {
    Instant other = Instant.parse("2030-06-01T00:00:01Z");

    User user = validBuilder().registeredAt(other).build();

    assertThat(user.created()).isEqualTo(other);
    assertThat(user.modified()).isEqualTo(other);
    assertThat(user.lastLogin()).isEqualTo(other);
  }

  @Test
  void exposesEveryValueItWasBuiltWith() {
    User user = validBuilder().build();

    assertThat(user.id()).isEqualTo(ID);
    assertThat(user.name()).isEqualTo("Juan Rodriguez");
    assertThat(user.email().value()).isEqualTo("juan@rodriguez.org");
    assertThat(user.passwordHash()).isEqualTo(HASH);
    assertThat(user.token()).isEqualTo(TOKEN);
    assertThat(user.phones()).containsExactly(new Phone("1234567", "1", "57"));
  }

  static Stream<Arguments> missingParts() {
    return Stream.of(
        part("id", b -> b.id(null)),
        part("email", b -> b.email(null)),
        part("passwordHash", b -> b.passwordHash(null)),
        part("passwordHash", b -> b.passwordHash("  ")),
        part("token", b -> b.token(null)),
        part("token", b -> b.token("")),
        part("registeredAt", b -> b.registeredAt(null)),
        part("phones", b -> b.phones(null)));
  }

  private static Arguments part(String name, Consumer<User.Builder> removal) {
    return Arguments.of(name, Named.of("without " + name, removal));
  }

  @ParameterizedTest
  @MethodSource("missingParts")
  void buildFailsNamingTheMissingPart(String part, Consumer<User.Builder> removal) {
    User.Builder builder = validBuilder();
    removal.accept(builder);

    assertThatIllegalStateException().isThrownBy(builder::build).withMessageContaining(part);
  }

  @Test
  void aBuilderThatNeverReceivedAnythingFailsOnTheFirstMissingPart() {
    assertThatIllegalStateException()
        .isThrownBy(() -> User.registration().build())
        .withMessageContaining("id");
  }

  @Test
  void theNameRuleAppliesAtBuildTime() {
    assertThat(reasonsOf(() -> validBuilder().name(null).build()))
        .containsExactly(Reason.NAME_REQUIRED);
    assertThat(reasonsOf(() -> validBuilder().name("  ").build()))
        .containsExactly(Reason.NAME_REQUIRED);
    assertThat(reasonsOf(() -> validBuilder().name("n".repeat(256)).build()))
        .containsExactly(Reason.NAME_TOO_LONG);
  }

  @Test
  void aUserWithoutPhonesIsValid() {
    User user = validBuilder().phones(List.of()).build();

    assertThat(user.phones()).isEmpty();
  }

  @Test
  void phonesDefaultToNoneWhenTheyAreNeverSupplied() {
    User user =
        User.registration()
            .id(ID)
            .name("Juan")
            .email(Email.of("juan@rodriguez.org", ANY_EMAIL))
            .passwordHash(HASH)
            .token(TOKEN)
            .registeredAt(NOW)
            .build();

    assertThat(user.phones()).isEmpty();
  }

  @Test
  void phonesKeepTheSubmittedOrder() {
    Phone first = new Phone("1111111", "1", "57");
    Phone second = new Phone("2222222", "2", "56");
    Phone third = new Phone("0000000", "3", "54");

    User user = validBuilder().phones(List.of(first, second, third)).build();

    assertThat(user.phones()).containsExactly(first, second, third);
  }

  @Test
  void thePhoneListIsUnmodifiable() {
    User user = validBuilder().build();

    assertThatThrownBy(() -> user.phones().add(new Phone("1", "1", "1")))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> user.phones().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void changingTheSourceListAfterBuildDoesNotChangeTheUser() {
    List<Phone> source = new ArrayList<>(List.of(new Phone("1111111", "1", "57")));
    User user = validBuilder().phones(source).build();

    source.add(new Phone("2222222", "2", "56"));

    assertThat(user.phones()).hasSize(1);
  }

  @Test
  void tenPhonesAreAcceptedAndElevenAreRejected() {
    assertThat(User.MAX_PHONES).isEqualTo(10);
    assertThat(validBuilder().phones(phones(10)).build().phones()).hasSize(10);
    assertThat(reasonsOf(() -> validBuilder().phones(phones(11)).build()))
        .containsExactly(Reason.PHONES_TOO_MANY);
  }

  @Test
  void phoneCountRuleIsExposedForCallersThatHaveNotBuiltPhonesYet() {
    assertThat(User.phoneCountViolation(0)).isEmpty();
    assertThat(User.phoneCountViolation(10)).isEmpty();
    assertThat(User.phoneCountViolation(11)).contains(Reason.PHONES_TOO_MANY);
  }

  private static List<Phone> phones(int count) {
    return IntStream.range(0, count).mapToObj(i -> new Phone("100000" + i, "1", "57")).toList();
  }

  @Test
  void toStringPrintsTheIdentifierOnly() {
    User user = validBuilder().build();

    assertThat(user).hasToString("User[id=" + ID.value() + "]");
    assertThat(user.toString())
        .doesNotContain(HASH)
        .doesNotContain(TOKEN)
        .doesNotContain("juan")
        .doesNotContain("Rodriguez")
        .doesNotContain("1234567");
  }

  @Test
  void usersAreIdentifiedByTheirId() {
    User first = validBuilder().build();
    User sameIdOtherData = validBuilder().name("Another Name").token("a.b.c").build();
    User otherId = validBuilder().id(UserId.generate()).build();

    assertThat(first).isEqualTo(sameIdOtherData).hasSameHashCodeAs(sameIdOtherData);
    assertThat(first).isNotEqualTo(otherId).isNotEqualTo("not a user");
  }

  @Test
  void eachRegistrationStartsFromAFreshBuilder() {
    assertThat(User.registration()).isNotSameAs(User.registration());
  }
}
