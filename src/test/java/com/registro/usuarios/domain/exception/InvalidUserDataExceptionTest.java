package com.registro.usuarios.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.registro.usuarios.domain.model.Reason;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class InvalidUserDataExceptionTest {

  @Test
  void catalogueHasExactlyTheNineteenRejectionReasons() {
    assertThat(Reason.values())
        .containsExactly(
            Reason.NAME_REQUIRED,
            Reason.NAME_TOO_LONG,
            Reason.EMAIL_REQUIRED,
            Reason.EMAIL_TOO_LONG,
            Reason.EMAIL_FORMAT,
            Reason.PASSWORD_REQUIRED,
            Reason.PASSWORD_TOO_LONG,
            Reason.PASSWORD_FORMAT,
            Reason.PHONES_TOO_MANY,
            Reason.PHONE_NULL,
            Reason.PHONE_NUMBER_REQUIRED,
            Reason.PHONE_NUMBER_TOO_LONG,
            Reason.PHONE_NUMBER_FORMAT,
            Reason.CITY_CODE_REQUIRED,
            Reason.CITY_CODE_TOO_LONG,
            Reason.CITY_CODE_FORMAT,
            Reason.COUNTRY_CODE_REQUIRED,
            Reason.COUNTRY_CODE_TOO_LONG,
            Reason.COUNTRY_CODE_FORMAT);
  }

  @Test
  void exposesTheReasonsItWasBuiltWith() {
    var exception =
        new InvalidUserDataException(Set.of(Reason.EMAIL_FORMAT, Reason.PASSWORD_REQUIRED));

    assertThat(exception.reasons())
        .containsExactlyInAnyOrder(Reason.EMAIL_FORMAT, Reason.PASSWORD_REQUIRED);
  }

  @Test
  void reasonsCannotBeModifiedByTheCaller() {
    var exception = new InvalidUserDataException(Set.of(Reason.NAME_REQUIRED));

    assertThatThrownBy(() -> exception.reasons().add(Reason.EMAIL_REQUIRED))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void laterChangesToTheSourceSetDoNotAffectTheException() {
    EnumSet<Reason> source = EnumSet.of(Reason.NAME_REQUIRED);
    var exception = new InvalidUserDataException(source);

    source.add(Reason.EMAIL_REQUIRED);

    assertThat(exception.reasons()).containsExactly(Reason.NAME_REQUIRED);
  }

  @Test
  void aRejectionWithoutReasonsIsAProgrammingError() {
    assertThatIllegalArgumentException().isThrownBy(() -> new InvalidUserDataException(Set.of()));
  }

  @Test
  void messageNamesTheReasonsAndNothingElse() {
    var exception = new InvalidUserDataException(Set.of(Reason.PASSWORD_FORMAT));

    assertThat(exception).hasMessage("Invalid user data: [PASSWORD_FORMAT]");
  }

  @Test
  void duplicateEmailRejectionIsADomainExceptionThatCarriesNoData() {
    var exception = new EmailAlreadyRegisteredException();

    assertThat(exception).isInstanceOf(DomainException.class);
    assertThat(exception).hasMessage("Email already registered");
  }

  @Test
  void bothRejectionsAreUncheckedDomainExceptions() {
    List<DomainException> exceptions =
        List.of(
            new InvalidUserDataException(Set.of(Reason.NAME_REQUIRED)),
            new EmailAlreadyRegisteredException());

    assertThat(exceptions).allSatisfy(e -> assertThat(e).isInstanceOf(RuntimeException.class));
  }
}
