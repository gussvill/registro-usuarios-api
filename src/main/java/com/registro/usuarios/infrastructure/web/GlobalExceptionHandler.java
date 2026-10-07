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
 * The single translation point from exceptions to the {@code {"mensaje": ...}} body. Extending
 * {@link ResponseEntityExceptionHandler} routes every standard Spring MVC exception through {@link
 * #handleExceptionInternal}, so the shape is replaced in one place.
 */
@RestControllerAdvice
class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(InvalidUserDataException.class)
  ResponseEntity<Object> invalidUserData(InvalidUserDataException rejected) {
    return answer(
        HttpStatus.BAD_REQUEST, new HttpHeaders(), ErrorMessages.joined(rejected.reasons()));
  }

  @ExceptionHandler(EmailAlreadyRegisteredException.class)
  ResponseEntity<Object> emailAlreadyRegistered() {
    return answer(HttpStatus.CONFLICT, new HttpHeaders(), ErrorMessages.EMAIL_ALREADY_REGISTERED);
  }

  /** Anything not mapped above. The stack trace stays in the log; the client gets fixed text. */
  @ExceptionHandler(Exception.class)
  ResponseEntity<Object> unexpected(Exception failure) {
    LOG.error("Unexpected failure while handling a request", failure);
    return answer(
        HttpStatus.INTERNAL_SERVER_ERROR, new HttpHeaders(), ErrorMessages.INTERNAL_ERROR);
  }

  /** The parser text can quote the payload, so it is neither logged nor returned. */
  @Override
  protected ResponseEntity<Object> handleHttpMessageNotReadable(
      HttpMessageNotReadableException failure,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    return answer(HttpStatus.BAD_REQUEST, headers, ErrorMessages.INVALID_BODY);
  }

  /** Discards the framework's ProblemDetail: every standard exception ends up here. */
  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception failure,
      Object body,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    return answer(status, headers, ErrorMessages.forStatus(status.value()));
  }

  /**
   * The only place an error response is built. The explicit JSON content type makes Spring skip
   * content negotiation, which would otherwise fail again for a client that does not accept JSON.
   */
  private static ResponseEntity<Object> answer(
      HttpStatusCode status, HttpHeaders headers, String mensaje) {
    return ResponseEntity.status(status)
        .headers(headers)
        .contentType(MediaType.APPLICATION_JSON)
        .body(new ErrorResponse(mensaje));
  }
}
