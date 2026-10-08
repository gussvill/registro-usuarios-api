package com.registro.usuarios.infrastructure.web;

import com.registro.usuarios.domain.exception.EmailAlreadyRegisteredException;
import com.registro.usuarios.domain.exception.InvalidUserDataException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * El único punto de traducción de excepciones al cuerpo {@code {"mensaje": ...}}. Al extender
 * {@link ResponseEntityExceptionHandler}, toda excepción estándar de Spring MVC pasa por {@link
 * #handleExceptionInternal}, de modo que la forma se reemplaza en un solo lugar.
 */
@RestControllerAdvice
class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);
  private static final int MAX_CAUSES = 20;

  @ExceptionHandler(InvalidUserDataException.class)
  ResponseEntity<Object> invalidUserData(InvalidUserDataException rejected) {
    return answer(
        HttpStatus.BAD_REQUEST, new HttpHeaders(), ErrorMessages.joined(rejected.reasons()));
  }

  @ExceptionHandler(EmailAlreadyRegisteredException.class)
  ResponseEntity<Object> emailAlreadyRegistered() {
    return answer(HttpStatus.CONFLICT, new HttpHeaders(), ErrorMessages.EMAIL_ALREADY_REGISTERED);
  }

  /**
   * Cualquier cosa no mapeada arriba. El log recibe las clases y los frames de pila del fallo y de
   * sus causas, y nunca un mensaje, porque un mensaje puede citar la petición (una base de datos
   * informa el valor que rechazó). El cliente recibe un texto fijo.
   */
  @ExceptionHandler(Exception.class)
  ResponseEntity<Object> unexpected(Exception failure) {
    LOG.error("Unexpected failure while handling a request: {}", withoutMessages(failure));
    return answer(
        HttpStatus.INTERNAL_SERVER_ERROR, new HttpHeaders(), ErrorMessages.INTERNAL_ERROR);
  }

  /**
   * El fallo representado como su clase, luego un bloque {@code Caused by:} por cada causa, cada
   * uno seguido de sus frames de pila, con el formato habitual de un stack trace pero sin mensaje
   * de excepción.
   */
  static String withoutMessages(Throwable failure) {
    StringBuilder text = new StringBuilder(failure.getClass().getName());
    appendFrames(text, failure);
    Throwable cause = failure.getCause();
    for (int depth = 0; cause != null && depth < MAX_CAUSES; depth++) {
      text.append(System.lineSeparator()).append("Caused by: ").append(cause.getClass().getName());
      appendFrames(text, cause);
      cause = cause.getCause() == cause ? null : cause.getCause();
    }
    return text.toString();
  }

  private static void appendFrames(StringBuilder text, Throwable failure) {
    for (StackTraceElement frame : failure.getStackTrace()) {
      text.append(System.lineSeparator()).append("\tat ").append(frame);
    }
  }

  /**
   * El texto del parser puede citar el contenido enviado, por eso no se registra ni se devuelve.
   */
  @Override
  protected ResponseEntity<Object> handleHttpMessageNotReadable(
      HttpMessageNotReadableException failure,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    return answer(HttpStatus.BAD_REQUEST, headers, ErrorMessages.INVALID_BODY);
  }

  /**
   * Descarta el ProblemDetail del framework: toda excepción estándar termina aquí. Un 5xx se
   * registra igual que un fallo inesperado, con clases y frames y sin mensaje; un 4xx es un error
   * del cliente y no se registra.
   */
  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception failure,
      Object body,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    if (status.is5xxServerError()) {
      LOG.error("Unexpected failure while handling a request: {}", withoutMessages(failure));
    }
    return answer(status, headers, ErrorMessages.forStatus(status.value()));
  }

  /**
   * El único lugar donde se construye una respuesta de error. El tipo de contenido JSON explícito
   * hace que Spring omita la negociación de contenido, que de otro modo volvería a fallar para un
   * cliente que no acepta JSON.
   */
  private static ResponseEntity<Object> answer(
      HttpStatusCode status, HttpHeaders headers, String mensaje) {
    return ResponseEntity.status(status)
        .headers(headers)
        .contentType(MediaType.APPLICATION_JSON)
        .body(new ErrorResponse(mensaje));
  }
}
