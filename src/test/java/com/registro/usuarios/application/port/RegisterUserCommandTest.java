package com.registro.usuarios.application.port;

import static org.assertj.core.api.Assertions.assertThat;

import com.registro.usuarios.application.port.RegisterUserCommand.PhoneData;
import java.util.List;
import org.junit.jupiter.api.Test;

class RegisterUserCommandTest {

  @Test
  void toStringRedactsThePasswordWhateverItIsEvenWhenItIsMissing() {
    var command =
        new RegisterUserCommand(
            "Juan Rodriguez",
            "juan@rodriguez.org",
            "hunter2",
            List.of(new PhoneData("1234567", "1", "57")));
    var withoutPassword = new RegisterUserCommand("Juan", "juan@rodriguez.org", null, null);

    assertThat(command.toString()).doesNotContain("hunter2").contains("password=<redacted>");
    assertThat(withoutPassword.toString()).contains("password=<redacted>").doesNotContain("null,");
  }
}
