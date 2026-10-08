package com.registro.usuarios.infrastructure.web;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Replaces Boot's error controller, which negotiates its content and can answer an HTML page or an
 * empty body. The servlet container forwards here the errors that never reached a handler (for
 * example a {@code sendError} from a filter), and the answer is the same {@code {"mensaje": ...}}
 * body as everywhere else. The JSON content type is set explicitly, so the client's {@code Accept}
 * header cannot change it.
 */
@Hidden
@RestController
@RequestMapping("${server.error.path:/error}")
class ApiErrorController implements ErrorController {

  private static final int DEFAULT_STATUS = 500;

  @RequestMapping
  ResponseEntity<ErrorResponse> error(HttpServletRequest request) {
    int status = statusOf(request);
    return ResponseEntity.status(status)
        .contentType(MediaType.APPLICATION_JSON)
        .body(new ErrorResponse(ErrorMessages.forStatus(status)));
  }

  private static int statusOf(HttpServletRequest request) {
    Object attribute = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
    return attribute instanceof Integer status ? status : DEFAULT_STATUS;
  }
}
