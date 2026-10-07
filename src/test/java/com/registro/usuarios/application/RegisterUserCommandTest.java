package com.registro.usuarios.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.registro.usuarios.application.RegisterUserCommand.PhoneData;
import java.util.List;
import org.junit.jupiter.api.Test;

class RegisterUserCommandTest {

  @Test
  void toStringRedactsThePassword() {
    var command =
        new RegisterUserCommand(
            "Juan Rodriguez",
            "juan@rodriguez.org",
            "hunter2",
            List.of(new PhoneData("1234567", "1", "57")));

    assertThat(command.toString()).doesNotContain("hunter2").contains("password=<redacted>");
  }

  @Test
  void toStringRedactsAPasswordThatIsMissingToo() {
    var command = new RegisterUserCommand("Juan", "juan@rodriguez.org", null, null);

    assertThat(command.toString()).contains("password=<redacted>").doesNotContain("null,");
  }

  @Test
  void keepsTheValuesItWasBuiltWith() {
    var phone = new PhoneData("1234567", "1", "57");
    var command = new RegisterUserCommand("Juan", "juan@rodriguez.org", "hunter2", List.of(phone));

    assertThat(command.name()).isEqualTo("Juan");
    assertThat(command.email()).isEqualTo("juan@rodriguez.org");
    assertThat(command.password()).isEqualTo("hunter2");
    assertThat(command.phones()).containsExactly(phone);
    assertThat(phone.number()).isEqualTo("1234567");
    assertThat(phone.cityCode()).isEqualTo("1");
    assertThat(phone.countryCode()).isEqualTo("57");
  }
}
