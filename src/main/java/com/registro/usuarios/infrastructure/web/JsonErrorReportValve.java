package com.registro.usuarios.infrastructure.web;

import java.io.IOException;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ErrorReportValve;
import tools.jackson.databind.json.JsonMapper;

/**
 * Tomcat writes an HTML error report for a request it rejects before any web application is chosen,
 * such as a path with a malformed percent escape. Such an error is never forwarded to the
 * application's error page, so the error controller cannot answer it. This valve takes the place of
 * Tomcat's report and writes the same {@code {"mensaje": ...}} body instead.
 */
class JsonErrorReportValve extends ErrorReportValve {

  private static final JsonMapper JSON = new JsonMapper();

  @Override
  protected void report(Request request, Response response, Throwable throwable) {
    int status = response.getStatus();
    if (status < 400 || response.getContentWritten() > 0 || !response.setErrorReported()) {
      return;
    }
    try {
      byte[] body = JSON.writeValueAsBytes(new ErrorResponse(ErrorMessages.forStatus(status)));
      response.setContentType("application/json");
      response.setCharacterEncoding("UTF-8");
      response.setContentLength(body.length);
      response.getOutputStream().write(body);
      response.finishResponse();
    } catch (IOException | IllegalStateException e) {
      // The connection is gone or the response is already being written: nothing is left to do.
    }
  }
}
