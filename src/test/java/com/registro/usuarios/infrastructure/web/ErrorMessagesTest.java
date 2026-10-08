package com.registro.usuarios.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.registro.usuarios.domain.model.Reason;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** El catálogo en español, fijado literalmente: los textos son parte del contrato de la API. */
class ErrorMessagesTest {

  /** Una fila por regla del catálogo de validación, exactamente como la establece el contrato. */
  private static final String[] CATALOGUE = {
    "NAME_REQUIRED, El nombre es obligatorio",
    "NAME_TOO_LONG, El nombre no debe superar 255 caracteres",
    "EMAIL_REQUIRED, El correo es obligatorio",
    "EMAIL_TOO_LONG, El correo no debe superar 254 caracteres",
    "EMAIL_FORMAT, El correo no tiene un formato válido",
    "PASSWORD_REQUIRED, La contraseña es obligatoria",
    "PASSWORD_TOO_LONG, La contraseña es demasiado larga",
    "PASSWORD_FORMAT, La contraseña no cumple el formato requerido",
    "PHONES_TOO_MANY, No se permiten más de 10 teléfonos",
    "PHONE_NULL, El teléfono no puede ser nulo",
    "PHONE_NUMBER_REQUIRED, El número de teléfono es obligatorio",
    "PHONE_NUMBER_TOO_LONG, El número de teléfono no debe superar 20 caracteres",
    "PHONE_NUMBER_FORMAT, El número de teléfono solo puede contener dígitos",
    "CITY_CODE_REQUIRED, El código de ciudad es obligatorio",
    "CITY_CODE_TOO_LONG, El código de ciudad no debe superar 10 caracteres",
    "CITY_CODE_FORMAT, El código de ciudad solo puede contener dígitos",
    "COUNTRY_CODE_REQUIRED, El código de país es obligatorio",
    "COUNTRY_CODE_TOO_LONG, El código de país no debe superar 10 caracteres",
    "COUNTRY_CODE_FORMAT, El código de país solo puede contener dígitos, con un + inicial opcional"
  };

  @Test
  void everyReasonHasItsLiteralMessage() {
    for (String row : CATALOGUE) {
      String[] cells = row.split(", ", 2);
      assertThat(ErrorMessages.of(Reason.valueOf(cells[0]))).as(cells[0]).isEqualTo(cells[1]);
    }
  }

  @Test
  void theCatalogueTableCoversEveryReasonExactlyOnce() {
    List<String> listed = new ArrayList<>();
    for (String row : CATALOGUE) {
      listed.add(row.split(", ", 2)[0]);
    }

    assertThat(listed)
        .containsExactlyInAnyOrderElementsOf(Stream.of(Reason.values()).map(Enum::name).toList());
  }

  @Test
  void aCombinationIsSortedAscendingAndJoinedWithSemicolonAndSpace() {
    assertThat(ErrorMessages.joined(EnumSet.of(Reason.NAME_REQUIRED, Reason.EMAIL_REQUIRED)))
        .isEqualTo("El correo es obligatorio; El nombre es obligatorio");
    assertThat(
            ErrorMessages.joined(
                EnumSet.of(Reason.PASSWORD_REQUIRED, Reason.NAME_REQUIRED, Reason.EMAIL_REQUIRED)))
        .isEqualTo(
            "El correo es obligatorio; El nombre es obligatorio; La contraseña es obligatoria");
  }

  @Test
  void theOrderFollowsTheSpanishTextAndNotTheEnumDeclaration() {
    // COUNTRY_CODE_REQUIRED se declara después de PHONE_NUMBER_REQUIRED, pero su texto ordena
    // antes.
    assertThat(
            ErrorMessages.joined(
                EnumSet.of(
                    Reason.PHONE_NUMBER_REQUIRED,
                    Reason.NAME_REQUIRED,
                    Reason.COUNTRY_CODE_REQUIRED,
                    Reason.CITY_CODE_REQUIRED)))
        .isEqualTo(
            "El código de ciudad es obligatorio; El código de país es obligatorio; "
                + "El nombre es obligatorio; El número de teléfono es obligatorio");
  }

  @Test
  void aSingleReasonIsNotDecorated() {
    assertThat(ErrorMessages.joined(EnumSet.of(Reason.PASSWORD_FORMAT)))
        .isEqualTo("La contraseña no cumple el formato requerido");
  }

  @ParameterizedTest
  @CsvSource({
    "400, La solicitud no es válida",
    "404, Recurso no encontrado",
    "405, Método no permitido",
    "406, Formato de respuesta no aceptable",
    "409, La solicitud entra en conflicto con el estado actual del recurso",
    "415, Tipo de contenido no soportado",
    "500, Error interno del servidor"
  })
  void eachMappedStatusHasItsLiteralMessage(int status, String message) {
    assertThat(ErrorMessages.forStatus(status)).isEqualTo(message);
  }

  @Test
  void aGenericConflictDoesNotClaimThatAnEmailIsRegistered() {
    assertThat(ErrorMessages.forStatus(409)).isNotEqualTo(ErrorMessages.EMAIL_ALREADY_REGISTERED);
    assertThat(ErrorMessages.EMAIL_ALREADY_REGISTERED).isEqualTo("El correo ya registrado");
  }

  @ParameterizedTest
  @ValueSource(ints = {401, 403, 408, 413, 422, 431})
  void anyOtherClientErrorIsAGenericBadRequestMessage(int status) {
    assertThat(ErrorMessages.forStatus(status)).isEqualTo("La solicitud no es válida");
  }

  @ParameterizedTest
  @ValueSource(ints = {501, 502, 503, 504})
  void anyOtherServerErrorIsTheGenericInternalMessage(int status) {
    assertThat(ErrorMessages.forStatus(status)).isEqualTo("Error interno del servidor");
  }

  @Test
  void everyLiteralIsNfcComposedSoAccentsAreSingleCodePoints() throws IllegalAccessException {
    List<String> literals = new ArrayList<>();
    for (Reason reason : Reason.values()) {
      literals.add(ErrorMessages.of(reason));
    }
    for (Field field : ErrorMessages.class.getDeclaredFields()) {
      if (Modifier.isStatic(field.getModifiers()) && field.getType() == String.class) {
        field.setAccessible(true);
        literals.add((String) field.get(null));
      }
    }

    assertThat(literals).hasSizeGreaterThan(Reason.values().length);
    for (String literal : literals) {
      assertThat(Normalizer.isNormalized(literal, Normalizer.Form.NFC)).as(literal).isTrue();
    }
  }

  @Test
  void anAccentedMessageEncodesToTheExpectedUtf8BytesByteForByte() {
    byte[] expected = {
      'L',
      'a',
      ' ',
      'c',
      'o',
      'n',
      't',
      'r',
      'a',
      's',
      'e',
      (byte) 0xC3,
      (byte) 0xB1,
      'a',
      ' ',
      'e',
      's',
      ' ',
      'o',
      'b',
      'l',
      'i',
      'g',
      'a',
      't',
      'o',
      'r',
      'i',
      'a'
    };

    byte[] actual = ErrorMessages.of(Reason.PASSWORD_REQUIRED).getBytes(StandardCharsets.UTF_8);

    assertThat(actual).isEqualTo(expected);
  }
}
