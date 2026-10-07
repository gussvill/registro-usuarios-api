package com.registro.usuarios.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.registro.usuarios.application.RegisterUserCommand;
import com.registro.usuarios.application.RegisterUserCommand.PhoneData;
import com.registro.usuarios.domain.model.Email;
import com.registro.usuarios.domain.model.Phone;
import com.registro.usuarios.domain.model.User;
import com.registro.usuarios.domain.model.UserId;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class UserWebMapperTest {

  private static final Instant AT = Instant.parse("2026-01-15T10:30:00.123456Z");
  private static final UUID ID = UUID.fromString("0b9e3b0e-6a4c-4d52-9b8e-1f1f2d6d7a10");

  private static User user(Phone... phones) {
    return User.registration()
        .id(new UserId(ID))
        .name("Juan Rodriguez")
        .email(Email.of("juan@rodriguez.org", Pattern.compile("^.+$")))
        .passwordHash("$2a$12$hash-that-must-never-be-exposed")
        .phones(List.of(phones))
        .token("a.b.c")
        .registeredAt(AT)
        .build();
  }

  @Test
  void aRequestBecomesTheCommandKeepingTheValuesAsReceived() {
    RegisterUserRequest request =
        new RegisterUserRequest(
            "  Juan  ",
            " Juan@Rodriguez.ORG ",
            "hunter2",
            List.of(new PhoneRequest("1234567", "1", "57"), new PhoneRequest("007", "02", "+56")));

    RegisterUserCommand command = UserWebMapper.toCommand(request);

    assertThat(command.name()).isEqualTo("  Juan  ");
    assertThat(command.email()).isEqualTo(" Juan@Rodriguez.ORG ");
    assertThat(command.password()).isEqualTo("hunter2");
    assertThat(command.phones())
        .containsExactly(new PhoneData("1234567", "1", "57"), new PhoneData("007", "02", "+56"));
  }

  @Test
  void absentFieldsStayAbsentSoThatTheDomainReportsThem() {
    RegisterUserCommand command =
        UserWebMapper.toCommand(new RegisterUserRequest(null, null, null, null));

    assertThat(command.name()).isNull();
    assertThat(command.email()).isNull();
    assertThat(command.password()).isNull();
    assertThat(command.phones()).isNull();
  }

  @Test
  void aNullPhoneEntryIsKeptAsNullSoThatTheDomainRejectsIt() {
    RegisterUserCommand command =
        UserWebMapper.toCommand(
            new RegisterUserRequest(
                "Juan",
                "juan@rodriguez.org",
                "hunter2",
                Arrays.asList(null, new PhoneRequest("1", "2", "3"))));

    assertThat(command.phones()).containsExactly(null, new PhoneData("1", "2", "3"));
  }

  @Test
  void aPhoneWithMissingPartsKeepsTheMissingParts() {
    RegisterUserCommand command =
        UserWebMapper.toCommand(
            new RegisterUserRequest(
                "Juan",
                "juan@rodriguez.org",
                "hunter2",
                List.of(new PhoneRequest(null, null, null))));

    assertThat(command.phones()).containsExactly(new PhoneData(null, null, null));
  }

  @Test
  void aUserBecomesTheResponseWithItsGeneratedFields() {
    UserResponse response = UserWebMapper.toResponse(user(new Phone("1234567", "1", "57")));

    assertThat(response.id()).isEqualTo(ID);
    assertThat(response.name()).isEqualTo("Juan Rodriguez");
    assertThat(response.email()).isEqualTo("juan@rodriguez.org");
    assertThat(response.created()).isEqualTo(AT);
    assertThat(response.modified()).isEqualTo(AT);
    assertThat(response.lastLogin()).isEqualTo(AT);
    assertThat(response.token()).isEqualTo("a.b.c");
    assertThat(response.active()).isTrue();
    assertThat(response.phones()).containsExactly(new PhoneResponse("1234567", "1", "57"));
  }

  @Test
  void phonesKeepTheirOrderAndAUserWithoutPhonesGetsAnEmptyList() {
    UserResponse two =
        UserWebMapper.toResponse(user(new Phone("2", "2", "2"), new Phone("1", "1", "1")));
    UserResponse none = UserWebMapper.toResponse(user());

    assertThat(two.phones())
        .containsExactly(new PhoneResponse("2", "2", "2"), new PhoneResponse("1", "1", "1"));
    assertThat(none.phones()).isNotNull().isEqualTo(Collections.emptyList());
  }

  @Test
  void theResponseTypesHaveNoComponentThatCouldCarryTheHashOrThePassword() {
    List<String> names =
        Arrays.stream(UserResponse.class.getRecordComponents())
            .map(RecordComponent::getName)
            .toList();

    assertThat(names)
        .containsExactly(
            "id", "name", "email", "phones", "created", "modified", "lastLogin", "token", "active");
    assertThat(names).noneMatch(name -> name.toLowerCase(java.util.Locale.ROOT).contains("pass"));
    assertThat(UserWebMapper.toResponse(user()).toString())
        .doesNotContain("hash-that-must-never-be-exposed");
  }
}
