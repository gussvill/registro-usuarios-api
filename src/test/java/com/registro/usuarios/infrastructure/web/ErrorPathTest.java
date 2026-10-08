package com.registro.usuarios.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.registro.usuarios.support.FullContextTest;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * La ruta de error y los errores que responde el dispatcher, sobre un puerto real: todo lo que
 * llega a la página de error de la aplicación es el mismo cuerpo JSON, para todo método y todo
 * valor de {@code Accept}. Se envían por un socket crudo porque MockMvc no realiza el despacho de
 * error del contenedor. Las peticiones que el contenedor de servlets rechaza antes de ejecutar
 * código de la aplicación quedan fuera del contrato y no se prueban aquí (véanse las limitaciones
 * conocidas).
 */
@FullContextTest
class ErrorPathTest {

  private static final int TIMEOUT_MILLIS = 5_000;
  private static final String SEND_ERROR_PREFIX = "/test-only/send-error/";
  private static final JsonMapper JSON = new JsonMapper();

  @Value("${local.server.port}")
  private int port;

  /**
   * Un filtro solo de prueba que responde {@code sendError} para las rutas marcadoras. Es lo que
   * reenvía el contenedor a la ruta de error sin pasar por ningún manejador de Spring MVC: el
   * camino que {@link ApiErrorController} existe para atender. Solo se registra en este contexto de
   * prueba.
   */
  @TestConfiguration(proxyBeanMethods = false)
  static class SendErrorFilterConfiguration {

    @Bean
    OncePerRequestFilter sendErrorFilter() {
      return new OncePerRequestFilter() {
        @Override
        protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
          String path = request.getRequestURI();
          if (path.startsWith(SEND_ERROR_PREFIX)) {
            response.sendError(Integer.parseInt(path.substring(SEND_ERROR_PREFIX.length())));
            return;
          }
          chain.doFilter(request, response);
        }
      };
    }
  }

  /**
   * Lo que volvió por el cable: estado, encabezados por nombre en minúsculas y el cuerpo
   * decodificado.
   */
  private record Wire(int status, Map<String, String> headers, String text) {

    String contentType() {
      return headers.getOrDefault("content-type", "");
    }
  }

  /**
   * Envía una petición HTTP/1.1 con el destino de petición crudo dado y lee la respuesta completa.
   */
  private Wire request(String method, String target, Map<String, String> extraHeaders)
      throws IOException {
    StringBuilder head =
        new StringBuilder(method + " " + target + " HTTP/1.1\r\nHost: localhost:" + port + "\r\n");
    extraHeaders.forEach(
        (name, value) -> head.append(name).append(": ").append(value).append("\r\n"));
    head.append("Connection: close\r\n");
    if (method.equals("POST")) {
      head.append("Content-Length: 0\r\n");
    }
    head.append("\r\n");

    try (Socket socket = new Socket()) {
      socket.connect(new InetSocketAddress("localhost", port), TIMEOUT_MILLIS);
      socket.setSoTimeout(TIMEOUT_MILLIS);
      socket.getOutputStream().write(head.toString().getBytes(StandardCharsets.ISO_8859_1));
      socket.getOutputStream().flush();
      return parse(socket.getInputStream().readAllBytes());
    }
  }

  private Wire get(String target) throws IOException {
    return request("GET", target, Map.of());
  }

  private static Wire parse(byte[] raw) throws IOException {
    int split = indexOf(raw, "\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1), 0);
    assertThat(split).as("una cabecera de respuesta HTTP completa").isPositive();
    String[] lines = new String(raw, 0, split, StandardCharsets.ISO_8859_1).split("\r\n", -1);
    int status = Integer.parseInt(lines[0].split(" ", 3)[1]);
    Map<String, String> headers = new LinkedHashMap<>();
    for (int i = 1; i < lines.length; i++) {
      int colon = lines[i].indexOf(':');
      headers.put(
          lines[i].substring(0, colon).toLowerCase(Locale.ROOT),
          lines[i].substring(colon + 1).strip());
    }
    byte[] body = new byte[raw.length - split - 4];
    System.arraycopy(raw, split + 4, body, 0, body.length);
    if ("chunked".equalsIgnoreCase(headers.get("transfer-encoding"))) {
      body = dechunk(body);
    }
    return new Wire(status, headers, new String(body, StandardCharsets.UTF_8));
  }

  private static byte[] dechunk(byte[] chunked) throws IOException {
    InputStream in = new ByteArrayInputStream(chunked);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    while (true) {
      StringBuilder sizeLine = new StringBuilder();
      for (int b = in.read(); b != '\n' && b != -1; b = in.read()) {
        if (b != '\r') {
          sizeLine.append((char) b);
        }
      }
      int size = Integer.parseInt(sizeLine.toString().split(";", 2)[0].strip(), 16);
      if (size == 0) {
        return out.toByteArray();
      }
      out.write(in.readNBytes(size));
      in.readNBytes(2);
    }
  }

  private static int indexOf(byte[] haystack, byte[] needle, int from) {
    for (int i = from; i <= haystack.length - needle.length; i++) {
      int j = 0;
      while (j < needle.length && haystack[i + j] == needle[j]) {
        j++;
      }
      if (j == needle.length) {
        return i;
      }
    }
    return -1;
  }

  /** Por el cable: un tipo de contenido JSON y un cuerpo que es solo "mensaje". */
  private static void assertContractShape(Wire wire, int status, String mensaje) {
    assertThat(wire.status()).as("estado").isEqualTo(status);
    assertThat(wire.contentType()).startsWith("application/json");
    JsonNode body = JSON.readTree(wire.text());
    assertThat(new ArrayList<>(body.propertyNames())).containsExactly("mensaje");
    assertThat(body.get("mensaje").asString()).isEqualTo(mensaje);
  }

  // --- la propia página de error de la aplicación, invocada directamente ---

  @Test
  void aDirectRequestToTheErrorPathWithoutAnErrorIsA404JsonContractBody() throws IOException {
    Wire wire = get("/error");

    assertContractShape(wire, 404, "Recurso no encontrado");
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = {"GET", "POST", "PUT", "DELETE", "PATCH"})
  void everyMethodOnTheErrorPathIsAJsonContractBody(String method) throws IOException {
    Wire wire = request(method, "/error", Map.of());

    assertContractShape(wire, 404, "Recurso no encontrado");
  }

  @Test
  void aBrowserHittingTheErrorPathDirectlyGetsJsonAndNotAWhitelabelPage() throws IOException {
    Wire wire = request("GET", "/error", Map.of("Accept", "text/html"));

    assertContractShape(wire, 404, "Recurso no encontrado");
    assertThat(wire.text()).doesNotContainIgnoringCase("whitelabel").doesNotContain("<");
  }

  // --- los errores propios del dispatcher, sobre un puerto real ---

  @Test
  void anUnknownPathOverARealPortIs404Json() throws IOException {
    Wire wire = get("/api/v1/does-not-exist");

    assertContractShape(wire, 404, "Recurso no encontrado");
  }

  @Test
  void aBrowserRequestingAnUnknownPathGetsJson() throws IOException {
    Wire wire = request("GET", "/nope", Map.of("Accept", "text/html"));

    assertContractShape(wire, 404, "Recurso no encontrado");
  }

  @Test
  void aWrongMethodOverARealPortIs405JsonWithAnAllowHeader() throws IOException {
    Wire wire = get("/api/v1/users");

    assertContractShape(wire, 405, "Método no permitido");
    assertThat(wire.headers().get("allow")).contains("POST");
  }

  @Test
  void anErrorBodyOverARealPortKeepsItsAccentsAsUtf8Bytes() throws IOException {
    Wire wire = get("/api/v1/users");

    // Decodificado como UTF-8 desde los bytes del cable: un escape o un carácter de reemplazo
    // serían distintos.
    assertThat(wire.text().getBytes(StandardCharsets.UTF_8))
        .isEqualTo("{\"mensaje\":\"Método no permitido\"}".getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void theHeadersOfTheErrorAnswersNameAnApplicationJsonTypeAndNeverHtmlOrPlainText()
      throws IOException {
    List<Wire> wires = List.of(get("/error"), get("/nope"), get("/api/v1/users"));

    assertThat(wires).hasSize(3);
    for (Wire wire : wires) {
      assertThat(wire.contentType()).doesNotContain("text/html").doesNotContain("text/plain");
      assertThat(wire.contentType()).startsWith("application/json");
    }
  }

  @Test
  void theErrorControllerIsNotPartOfThePublishedApiDocument() throws IOException {
    Wire wire = get("/v3/api-docs");

    assertThat(wire.status()).isEqualTo(200);
    assertThat(new ArrayList<>(JSON.readTree(wire.text()).get("paths").propertyNames()))
        .containsExactly("/api/v1/users");
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = {"POST", "PUT", "DELETE"})
  void aWriteToAnUnknownPathIs404NotAMethodError(String method) throws IOException {
    Wire wire = request(method, "/api/v1/does-not-exist", Map.of());

    assertContractShape(wire, 404, "Recurso no encontrado");
  }

  // --- errores reenviados por el contenedor a la ruta de error ---

  @ParameterizedTest(name = "sendError({0}) is answered as {1}")
  @CsvSource({
    "503, Error interno del servidor",
    "500, Error interno del servidor",
    "400, La solicitud no es válida",
    "401, La solicitud no es válida",
    "404, Recurso no encontrado",
    "409, La solicitud entra en conflicto con el estado actual del recurso"
  })
  void anErrorSentByAFilterIsForwardedToTheErrorPathAndAnsweredWithTheContractBody(
      int status, String mensaje) throws IOException {
    Wire wire = get(SEND_ERROR_PREFIX + status);

    assertContractShape(wire, status, mensaje);
  }

  @Test
  void anErrorSentByAFilterIsJsonEvenForABrowser() throws IOException {
    Wire wire = request("GET", SEND_ERROR_PREFIX + 503, Map.of("Accept", "text/html"));

    assertContractShape(wire, 503, "Error interno del servidor");
    assertThat(wire.text()).doesNotContainIgnoringCase("whitelabel").doesNotContain("<");
  }

  @Test
  void anErrorSentByAFilterOnAWriteMethodIsTheSameContractBody() throws IOException {
    Wire wire = request("POST", SEND_ERROR_PREFIX + 503, Map.of());

    assertContractShape(wire, 503, "Error interno del servidor");
  }
}
