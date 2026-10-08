package com.registro.usuarios.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * Las ramas de {@link ApiErrorController} que no se pueden provocar desde fuera: el atributo de
 * estado del contenedor ausente, entero y no entero. El reenvío real, por HTTP, lo prueba {@link
 * ErrorPathTest}.
 */
class ApiErrorControllerTest {

  private final ApiErrorController controller = new ApiErrorController();

  private ResponseEntity<ErrorResponse> answer(Object statusAttribute) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    if (statusAttribute != null) {
      request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, statusAttribute);
    }
    return controller.error(request);
  }

  @ParameterizedTest(name = "status attribute {0}")
  @ValueSource(ints = {400, 404, 409, 500, 503})
  void anIntegerStatusAttributeIsTheStatusOfTheAnswer(int status) {
    ResponseEntity<ErrorResponse> reply = answer(status);

    assertThat(reply.getStatusCode().value()).isEqualTo(status);
    assertThat(reply.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
    assertThat(reply.getBody().mensaje()).isEqualTo(ErrorMessages.forStatus(status));
  }

  @Test
  void aStatusAttributeThatIsNotAnIntegerIsAnInternalError() {
    ResponseEntity<ErrorResponse> reply = answer("503");

    assertThat(reply.getStatusCode().value()).isEqualTo(500);
    assertThat(reply.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
    assertThat(reply.getBody().mensaje()).isEqualTo("Error interno del servidor");
  }

  @Test
  void aLongStatusAttributeIsNotReadAsAnInteger() {
    assertThat(answer(404L).getStatusCode().value()).isEqualTo(500);
  }

  @Test
  void noStatusAttributeIsARequestForAPageThatDoesNotExist() {
    ResponseEntity<ErrorResponse> reply = answer(null);

    assertThat(reply.getStatusCode().value()).isEqualTo(404);
    assertThat(reply.getBody().mensaje()).isEqualTo("Recurso no encontrado");
  }
}
