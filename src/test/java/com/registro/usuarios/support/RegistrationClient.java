package com.registro.usuarios.support;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * A small HTTP client for the full-context tests: real requests to the random port, bodies and
 * answers as UTF-8 text, and a hard timeout so that a stuck request can never hang the suite.
 */
public final class RegistrationClient {

  /** The literal body of the exercise statement. */
  public static final String STATEMENT_BODY =
      "{\"name\":\"Juan Rodriguez\",\"email\":\"juan@rodriguez.org\",\"password\":\"hunter2\","
          + "\"phones\":[{\"number\":\"1234567\",\"citycode\":\"1\",\"contrycode\":\"57\"}]}";

  public static final JsonMapper JSON = new JsonMapper();

  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
  private static final HttpClient HTTP =
      HttpClient.newBuilder()
          .version(HttpClient.Version.HTTP_1_1)
          .connectTimeout(Duration.ofSeconds(5))
          .build();

  /** What came back: status, content type, headers and the body decoded as UTF-8. */
  public record Reply(int status, String contentType, HttpHeaders headers, String body) {

    public JsonNode json() {
      return JSON.readTree(body);
    }

    public String header(String name) {
      return headers.firstValue(name).orElse("");
    }
  }

  private final String baseUrl;

  public RegistrationClient(int port) {
    this.baseUrl = "http://localhost:" + port;
  }

  /** An address that has never been registered, valid under the default email pattern. */
  public static String uniqueEmail() {
    return "u" + UUID.randomUUID() + "@dominio.cl";
  }

  /** A valid registration body for the given email, with one phone. */
  public static ObjectNode validBody(String email) {
    ObjectNode body = JSON.createObjectNode();
    body.put("name", "Juan Rodriguez");
    body.put("email", email);
    body.put("password", "hunter2");
    body.putArray("phones")
        .addObject()
        .put("number", "1234567")
        .put("citycode", "1")
        .put("contrycode", "57");
    return body;
  }

  /**
   * Removes a user and its phones, so a test that needs a fixed address does not depend on order.
   */
  public static void forget(JdbcTemplate jdbc, String email) {
    jdbc.update(
        "DELETE FROM phones WHERE user_id IN (SELECT id FROM users WHERE email = ?)", email);
    jdbc.update("DELETE FROM users WHERE email = ?", email);
  }

  /** POST /api/v1/users as JSON, with no Accept header. */
  public Reply post(String body) throws IOException, InterruptedException {
    return send("POST", "/api/v1/users", "application/json", null, body);
  }

  public Reply post(JsonNode body) throws IOException, InterruptedException {
    return post(body.toString());
  }

  public Reply send(String method, String path, String contentType, String accept, String body)
      throws IOException, InterruptedException {
    return HTTP.send(request(method, path, contentType, accept, body), handler()).body();
  }

  public CompletableFuture<Reply> postAsync(String body) {
    return HTTP.sendAsync(
            request("POST", "/api/v1/users", "application/json", null, body), handler())
        .thenApply(HttpResponse::body);
  }

  public Reply get(String path) throws IOException, InterruptedException {
    return send("GET", path, null, null, null);
  }

  private HttpRequest request(
      String method, String path, String contentType, String accept, String body) {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder(URI.create(baseUrl + path))
            .timeout(REQUEST_TIMEOUT)
            .method(
                method,
                body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
    if (contentType != null) {
      builder.header("Content-Type", contentType);
    }
    if (accept != null) {
      builder.header("Accept", accept);
    }
    return builder.build();
  }

  private static HttpResponse.BodyHandler<Reply> handler() {
    return info ->
        HttpResponse.BodySubscribers.mapping(
            HttpResponse.BodySubscribers.ofByteArray(),
            bytes ->
                new Reply(
                    info.statusCode(),
                    info.headers().firstValue("Content-Type").orElse(""),
                    info.headers(),
                    new String(bytes, StandardCharsets.UTF_8)));
  }
}
