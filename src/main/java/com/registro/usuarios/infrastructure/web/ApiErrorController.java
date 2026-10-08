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
 * Reemplaza el controlador de errores de Boot, que negocia su contenido y puede responder una
 * página HTML o un cuerpo vacío. El contenedor de servlets reenvía aquí los errores que nunca
 * llegaron a un manejador (por ejemplo un {@code sendError} desde un filtro), y la respuesta es el
 * mismo cuerpo {@code {"mensaje": ...}} que en el resto de la API. El tipo de contenido JSON se
 * fija de forma explícita, de modo que el encabezado {@code Accept} del cliente no pueda cambiarlo.
 */
@Hidden
@RestController
@RequestMapping("${server.error.path:/error}")
class ApiErrorController implements ErrorController {

  private static final int NO_ERROR_STATUS = 404;
  private static final int UNREADABLE_STATUS = 500;

  @RequestMapping
  ResponseEntity<ErrorResponse> error(HttpServletRequest request) {
    int status = statusOf(request);
    return ResponseEntity.status(status)
        .contentType(MediaType.APPLICATION_JSON)
        .body(new ErrorResponse(ErrorMessages.forStatus(status)));
  }

  private static int statusOf(HttpServletRequest request) {
    Object attribute = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
    if (attribute == null) {
      // No se reenvió nada: una petición directa a la ruta de error es una petición de una página
      // inexistente.
      return NO_ERROR_STATUS;
    }
    return attribute instanceof Integer status ? status : UNREADABLE_STATUS;
  }
}
