package com.registro.usuarios.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.registro.usuarios.application.port.RegisterUser;
import com.registro.usuarios.application.port.RegisterUserCommand;
import com.registro.usuarios.application.port.RegisterUserCommand.PhoneData;
import com.registro.usuarios.domain.model.Email;
import com.registro.usuarios.domain.model.Phone;
import com.registro.usuarios.domain.model.User;
import com.registro.usuarios.domain.model.UserId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * El contrato JSON de la respuesta de éxito, con el caso de uso reemplazado por un mock:
 * exactamente las claves documentadas, los nombres literales del enunciado, instantes ISO-8601 y
 * ningún secreto en ninguna parte. Lo que ocurre con una petición que el caso de uso rechaza
 * pertenece al contrato de errores ({@code ErrorContractTest}). La tipificación estricta de cadenas
 * se importa, como en producción.
 */
@WebMvcTest(UserController.class)
@Import(JacksonConfig.class)
class UserControllerTest {

  private static final String STATEMENT_BODY =
      "{\"name\":\"Juan Rodriguez\",\"email\":\"juan@rodriguez.org\",\"password\":\"hunter2\","
          + "\"phones\":[{\"number\":\"1234567\",\"citycode\":\"1\",\"contrycode\":\"57\"}]}";
  private static final String HASH = "$2a$12$abcdefghijklmnopqrstuuABCDEFGHIJKLMNOPQRSTUVWXYZ01234";
  private static final String TOKEN = "header.payload.signature";
  private static final Pattern ISO_INSTANT =
      Pattern.compile("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{1,9})?Z$");
  private static final UUID ID = UUID.fromString("0b9e3b0e-6a4c-4d52-9b8e-1f1f2d6d7a10");

  @Autowired private MockMvc mvc;
  @MockitoBean private RegisterUser useCase;
  private final JsonMapper json = new JsonMapper();

  private static User user(String email, Instant at, Phone... phones) {
    return User.registration()
        .id(new UserId(ID))
        .name("Juan Rodriguez")
        .email(Email.of(email, Pattern.compile("^.+$")))
        .passwordHash(HASH)
        .phones(List.of(phones))
        .token(TOKEN)
        .registeredAt(at)
        .build();
  }

  private MvcResult register(String body) throws Exception {
    return mvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(body))
        .andReturn();
  }

  private static List<String> keysOf(JsonNode node) {
    return new ArrayList<>(node.propertyNames());
  }

  @Test
  void theStatementBodyIsAnsweredWith201AndExactlyTheDocumentedKeysInOrder() throws Exception {
    Instant at = Instant.parse("2026-01-15T10:30:00Z");
    when(useCase.register(any()))
        .thenReturn(user("juan@rodriguez.org", at, new Phone("1234567", "1", "57")));

    MvcResult result = register(STATEMENT_BODY);

    assertThat(result.getResponse().getStatus()).isEqualTo(201);
    assertThat(result.getResponse().getContentType()).startsWith("application/json");
    JsonNode body = json.readTree(result.getResponse().getContentAsString());
    assertThat(keysOf(body))
        .containsExactly(
            "id",
            "name",
            "email",
            "phones",
            "created",
            "modified",
            "last_login",
            "token",
            "isactive");
    assertThat(body.get("id").asString()).isEqualTo("0b9e3b0e-6a4c-4d52-9b8e-1f1f2d6d7a10");
    assertThat(body.get("name").asString()).isEqualTo("Juan Rodriguez");
    assertThat(body.get("email").asString()).isEqualTo("juan@rodriguez.org");
    assertThat(body.get("token").asString()).isEqualTo(TOKEN);
    assertThat(body.get("isactive").isBoolean()).isTrue();
    assertThat(body.get("isactive").asBoolean()).isTrue();
  }

  @Test
  void theCreatedResponseCarriesTheBearerTokenSoItIsNotCacheable() throws Exception {
    when(useCase.register(any()))
        .thenReturn(
            user(
                "juan@rodriguez.org",
                Instant.parse("2026-01-15T10:30:00Z"),
                new Phone("1234567", "1", "57")));

    mvc.perform(
            post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(STATEMENT_BODY))
        .andExpect(status().isCreated())
        .andExpect(header().string("Cache-Control", "no-store"));
  }

  @Test
  void eachPhoneHasExactlyNumberCitycodeAndContrycode() throws Exception {
    when(useCase.register(any()))
        .thenReturn(
            user(
                "juan@rodriguez.org",
                Instant.parse("2026-01-15T10:30:00Z"),
                new Phone("1234567", "1", "57")));

    JsonNode phones =
        json.readTree(register(STATEMENT_BODY).getResponse().getContentAsString()).get("phones");

    assertThat(phones.isArray()).isTrue();
    assertThat(phones).hasSize(1);
    assertThat(keysOf(phones.get(0))).containsExactly("number", "citycode", "contrycode");
    assertThat(phones.get(0).get("number").asString()).isEqualTo("1234567");
    assertThat(phones.get(0).get("citycode").asString()).isEqualTo("1");
    assertThat(phones.get(0).get("contrycode").asString()).isEqualTo("57");
  }

  @Test
  void theRequestIsMappedToTheCommandWithTheLiteralNamesOfTheStatement() throws Exception {
    when(useCase.register(any()))
        .thenReturn(user("juan@rodriguez.org", Instant.parse("2026-01-15T10:30:00Z")));

    register(STATEMENT_BODY);

    ArgumentCaptor<RegisterUserCommand> command =
        ArgumentCaptor.forClass(RegisterUserCommand.class);
    verify(useCase).register(command.capture());
    assertThat(command.getValue().name()).isEqualTo("Juan Rodriguez");
    assertThat(command.getValue().email()).isEqualTo("juan@rodriguez.org");
    assertThat(command.getValue().password()).isEqualTo("hunter2");
    assertThat(command.getValue().phones()).containsExactly(new PhoneData("1234567", "1", "57"));
  }

  @Test
  void anotherRequestIsMappedToAnotherCommand() throws Exception {
    when(useCase.register(any()))
        .thenReturn(user("maria@dominio.cl", Instant.parse("2026-01-15T10:30:00Z")));

    register(
        "{\"name\":\"Maria\",\"email\":\"MARIA@dominio.cl\",\"password\":\"secret99\","
            + "\"phones\":[{\"number\":\"7654321\",\"citycode\":\"2\",\"contrycode\":\"56\"},"
            + "{\"number\":\"1111111\",\"citycode\":\"3\",\"contrycode\":\"54\"}]}");

    ArgumentCaptor<RegisterUserCommand> command =
        ArgumentCaptor.forClass(RegisterUserCommand.class);
    verify(useCase).register(command.capture());
    assertThat(command.getValue().name()).isEqualTo("Maria");
    assertThat(command.getValue().email()).isEqualTo("MARIA@dominio.cl");
    assertThat(command.getValue().password()).isEqualTo("secret99");
    assertThat(command.getValue().phones())
        .containsExactly(new PhoneData("7654321", "2", "56"), new PhoneData("1111111", "3", "54"));
  }

  @Test
  void theThreeTimestampsAreIso8601InstantsEndingInZ() throws Exception {
    Instant whole = Instant.parse("2026-01-15T10:30:00Z");
    Instant micros = Instant.parse("2026-01-15T10:30:00.123456Z");

    for (Instant at : List.of(whole, micros)) {
      when(useCase.register(any())).thenReturn(user("juan@rodriguez.org", at));

      JsonNode body = json.readTree(register(STATEMENT_BODY).getResponse().getContentAsString());

      for (String key : List.of("created", "modified", "last_login")) {
        assertThat(body.get(key).asString()).matches(ISO_INSTANT).isEqualTo(at.toString());
      }
    }
  }

  @Test
  void neitherThePasswordNorTheHashAppearAnywhereInTheResponse() throws Exception {
    when(useCase.register(any()))
        .thenReturn(
            user(
                "juan@rodriguez.org",
                Instant.parse("2026-01-15T10:30:00Z"),
                new Phone("1234567", "1", "57")));

    String raw = register(STATEMENT_BODY).getResponse().getContentAsString();

    assertThat(raw).doesNotContain("hunter2").doesNotContain(HASH).doesNotContain("$2a$");
    assertThat(raw.toLowerCase(java.util.Locale.ROOT)).doesNotContain("password");
  }

  @Test
  void aUserWithoutPhonesIsAnsweredWithAnEmptyArrayNotNullNorOmitted() throws Exception {
    when(useCase.register(any()))
        .thenReturn(user("juan@rodriguez.org", Instant.parse("2026-01-15T10:30:00Z")));

    JsonNode body =
        json.readTree(
            register(
                    "{\"name\":\"Juan\",\"email\":\"juan@rodriguez.org\",\"password\":\"hunter2\"}")
                .getResponse()
                .getContentAsString());

    assertThat(body.has("phones")).isTrue();
    assertThat(body.get("phones").isArray()).isTrue();
    assertThat(body.get("phones")).isEmpty();
  }

  @Test
  void aMissingPhonesPropertyReachesTheUseCaseAsNothingToStore() throws Exception {
    when(useCase.register(any()))
        .thenReturn(user("juan@rodriguez.org", Instant.parse("2026-01-15T10:30:00Z")));

    register("{\"name\":\"Juan\",\"email\":\"juan@rodriguez.org\",\"password\":\"hunter2\"}");

    ArgumentCaptor<RegisterUserCommand> command =
        ArgumentCaptor.forClass(RegisterUserCommand.class);
    verify(useCase).register(command.capture());
    assertThat(command.getValue().phones()).isNull();
  }

  @Test
  void phonesAreReturnedInTheOrderTheyWereSubmitted() throws Exception {
    Instant at = Instant.parse("2026-01-15T10:30:00Z");
    Phone first = new Phone("1111111", "1", "57");
    Phone second = new Phone("2222222", "2", "56");

    when(useCase.register(any())).thenReturn(user("juan@rodriguez.org", at, first, second));
    JsonNode ascending =
        json.readTree(register(STATEMENT_BODY).getResponse().getContentAsString()).get("phones");
    when(useCase.register(any())).thenReturn(user("juan@rodriguez.org", at, second, first));
    JsonNode descending =
        json.readTree(register(STATEMENT_BODY).getResponse().getContentAsString()).get("phones");

    assertThat(ascending.get(0).get("number").asString()).isEqualTo("1111111");
    assertThat(ascending.get(1).get("number").asString()).isEqualTo("2222222");
    assertThat(descending.get(0).get("number").asString()).isEqualTo("2222222");
    assertThat(descending.get(1).get("number").asString()).isEqualTo("1111111");
  }

  @Test
  void aWildcardAcceptAndNoAcceptAtAllAreBothServedAsJson() throws Exception {
    when(useCase.register(any()))
        .thenReturn(user("juan@rodriguez.org", Instant.parse("2026-01-15T10:30:00Z")));

    mvc.perform(
            post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.ALL)
                .content(STATEMENT_BODY))
        .andExpect(status().isCreated())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.name").value("Juan Rodriguez"));
    mvc.perform(
            post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(STATEMENT_BODY))
        .andExpect(status().isCreated())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.name").value("Juan Rodriguez"));
    mvc.perform(
            post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(STATEMENT_BODY))
        .andExpect(status().isCreated())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
  }

  @Test
  void clientSuppliedGeneratedFieldsAreIgnored() throws Exception {
    when(useCase.register(any()))
        .thenReturn(user("juan@rodriguez.org", Instant.parse("2026-01-15T10:30:00Z")));
    String supplied = "99999999-9999-9999-9999-999999999999";

    MvcResult result =
        register(
            "{\"name\":\"Juan Rodriguez\",\"email\":\"juan@rodriguez.org\",\"password\":\"hunter2\","
                + "\"id\":\""
                + supplied
                + "\",\"token\":\"client.token.value\",\"isactive\":false,"
                + "\"created\":\"1999-01-01T00:00:00Z\",\"modified\":\"1999-01-01T00:00:00Z\","
                + "\"last_login\":\"1999-01-01T00:00:00Z\"}");

    assertThat(result.getResponse().getStatus()).isEqualTo(201);
    JsonNode body = json.readTree(result.getResponse().getContentAsString());
    assertThat(body.get("id").asString()).isEqualTo(ID.toString()).isNotEqualTo(supplied);
    assertThat(body.get("token").asString()).isEqualTo(TOKEN);
    assertThat(body.get("isactive").asBoolean()).isTrue();
    assertThat(body.get("created").asString()).isEqualTo("2026-01-15T10:30:00Z");
    assertThat(body.get("last_login").asString()).isEqualTo("2026-01-15T10:30:00Z");
  }

  @Test
  void theCreatedUserHasNoLocationHeaderBecauseThereIsNoResourceToFetch() throws Exception {
    when(useCase.register(any()))
        .thenReturn(user("juan@rodriguez.org", Instant.parse("2026-01-15T10:30:00Z")));

    mvc.perform(
            post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(STATEMENT_BODY))
        .andExpect(status().isCreated())
        .andExpect(header().doesNotExist("Location"));
  }

  // El estado de las peticiones que el endpoint no consume ni produce pertenece a su mapeo.
  // El cuerpo de esas respuestas pertenece al contrato de errores y no se verifica aquí.

  @Test
  void aBodyThatIsNotJsonIsNotConsumed() throws Exception {
    mvc.perform(post("/api/v1/users").contentType(MediaType.TEXT_PLAIN).content(STATEMENT_BODY))
        .andExpect(status().isUnsupportedMediaType());

    verifyNoInteractions(useCase);
  }

  @Test
  void aClientThatOnlyAcceptsXmlIsNotServed() throws Exception {
    mvc.perform(
            post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_XML)
                .content(STATEMENT_BODY))
        .andExpect(status().isNotAcceptable());

    verifyNoInteractions(useCase);
  }
}
