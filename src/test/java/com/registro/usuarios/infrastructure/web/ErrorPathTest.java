package com.registro.usuarios.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.registro.usuarios.support.FullContextTest;
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
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Value;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The error path and the errors the dispatcher answers, over a real port: whatever reaches the
 * application's error page is the same JSON body, for every method and every {@code Accept} value.
 * They are sent over a raw socket because MockMvc does not perform the container's error dispatch.
 * Requests the servlet container rejects before any application code runs are outside the contract
 * and are not tested here (see the known limitations).
 */
@FullContextTest
class ErrorPathTest {

  private static final int TIMEOUT_MILLIS = 5_000;
  private static final JsonMapper JSON = new JsonMapper();

  @Value("${local.server.port}")
  private int port;

  /** What came back on the wire: status, headers by lower-cased name, and the decoded body. */
  private record Wire(int status, Map<String, String> headers, String text) {

    String contentType() {
      return headers.getOrDefault("content-type", "");
    }
  }

  /** Sends one HTTP/1.1 request with the given raw request target and reads the whole answer. */
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
    assertThat(split).as("a complete HTTP response head").isPositive();
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

  /** On the wire: a JSON content type and a body that is only "mensaje". */
  private static void assertContractShape(Wire wire, int status, String mensaje) {
    assertThat(wire.status()).as("status").isEqualTo(status);
    assertThat(wire.contentType()).startsWith("application/json");
    JsonNode body = JSON.readTree(wire.text());
    assertThat(new ArrayList<>(body.propertyNames())).containsExactly("mensaje");
    assertThat(body.get("mensaje").asString()).isEqualTo(mensaje);
  }

  // --- the application's own error page, hit directly ---

  @Test
  void aDirectRequestToTheErrorPathIsAJsonContractBody() throws IOException {
    Wire wire = get("/error");

    assertContractShape(wire, 500, "Error interno del servidor");
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = {"GET", "POST", "PUT", "DELETE", "PATCH"})
  void everyMethodOnTheErrorPathIsAJsonContractBody(String method) throws IOException {
    Wire wire = request(method, "/error", Map.of());

    assertThat(wire.contentType()).startsWith("application/json");
    JsonNode body = JSON.readTree(wire.text());
    assertThat(new ArrayList<>(body.propertyNames())).containsExactly("mensaje");
  }

  @Test
  void aBrowserHittingTheErrorPathDirectlyGetsJsonAndNotAWhitelabelPage() throws IOException {
    Wire wire = request("GET", "/error", Map.of("Accept", "text/html"));

    assertContractShape(wire, 500, "Error interno del servidor");
    assertThat(wire.text()).doesNotContainIgnoringCase("whitelabel").doesNotContain("<");
  }

  // --- the dispatcher's own errors, over a real port ---

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

    // Decoded as UTF-8 from the wire bytes: an escape or a replacement character would differ.
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
}
