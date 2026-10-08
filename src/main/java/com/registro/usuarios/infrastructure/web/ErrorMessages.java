package com.registro.usuarios.infrastructure.web;

import com.registro.usuarios.domain.model.Reason;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * The Spanish message catalogue of the error contract. The domain only knows typed reasons; the
 * client-facing text is presentation and lives here. The literals are NFC-composed in a UTF-8
 * source file, so an accented letter is a single code point.
 */
final class ErrorMessages {

  static final String INVALID_BODY = "El cuerpo de la solicitud no es válido";
  static final String INVALID_REQUEST = "La solicitud no es válida";
  static final String NOT_FOUND = "Recurso no encontrado";
  static final String METHOD_NOT_ALLOWED = "Método no permitido";
  static final String NOT_ACCEPTABLE = "Formato de respuesta no aceptable";
  static final String EMAIL_ALREADY_REGISTERED = "El correo ya registrado";
  static final String CONFLICT = "La solicitud entra en conflicto con el estado actual del recurso";
  static final String UNSUPPORTED_MEDIA_TYPE = "Tipo de contenido no soportado";
  static final String INTERNAL_ERROR = "Error interno del servidor";

  private static final String SEPARATOR = "; ";

  private ErrorMessages() {}

  /** The catalogue text of one validation rule. A new reason without a text does not compile. */
  static String of(Reason reason) {
    return switch (reason) {
      case NAME_REQUIRED -> "El nombre es obligatorio";
      case NAME_TOO_LONG -> "El nombre no debe superar 255 caracteres";
      case EMAIL_REQUIRED -> "El correo es obligatorio";
      case EMAIL_TOO_LONG -> "El correo no debe superar 254 caracteres";
      case EMAIL_FORMAT -> "El correo no tiene un formato válido";
      case PASSWORD_REQUIRED -> "La contraseña es obligatoria";
      case PASSWORD_TOO_LONG -> "La contraseña es demasiado larga";
      case PASSWORD_FORMAT -> "La contraseña no cumple el formato requerido";
      case PHONES_TOO_MANY -> "No se permiten más de 10 teléfonos";
      case PHONE_NULL -> "El teléfono no puede ser nulo";
      case PHONE_NUMBER_REQUIRED -> "El número de teléfono es obligatorio";
      case PHONE_NUMBER_TOO_LONG -> "El número de teléfono no debe superar 20 caracteres";
      case PHONE_NUMBER_FORMAT -> "El número de teléfono solo puede contener dígitos";
      case CITY_CODE_REQUIRED -> "El código de ciudad es obligatorio";
      case CITY_CODE_TOO_LONG -> "El código de ciudad no debe superar 10 caracteres";
      case CITY_CODE_FORMAT -> "El código de ciudad solo puede contener dígitos";
      case COUNTRY_CODE_REQUIRED -> "El código de país es obligatorio";
      case COUNTRY_CODE_TOO_LONG -> "El código de país no debe superar 10 caracteres";
      case COUNTRY_CODE_FORMAT ->
          "El código de país solo puede contener dígitos, con un + inicial opcional";
    };
  }

  /**
   * The distinct messages of the reasons, in ascending natural string order, joined with {@code ";
   * "}. The order is that of the Spanish text, not of the enum, because the contract fixes it.
   */
  static String joined(Set<Reason> reasons) {
    return reasons.stream()
        .map(ErrorMessages::of)
        .collect(Collectors.toCollection(TreeSet::new))
        .stream()
        .collect(Collectors.joining(SEPARATOR));
  }

  /**
   * The message for a status that did not come from a typed rejection. Used by the advice, by the
   * error controller and by anything else that must answer without application context.
   */
  static String forStatus(int status) {
    return switch (status) {
      case 400 -> INVALID_REQUEST;
      case 404 -> NOT_FOUND;
      case 405 -> METHOD_NOT_ALLOWED;
      case 406 -> NOT_ACCEPTABLE;
      case 409 -> CONFLICT;
      case 415 -> UNSUPPORTED_MEDIA_TYPE;
      default -> status >= 400 && status < 500 ? INVALID_REQUEST : INTERNAL_ERROR;
    };
  }
}
