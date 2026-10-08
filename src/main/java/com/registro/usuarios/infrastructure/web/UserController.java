package com.registro.usuarios.infrastructure.web;

import com.registro.usuarios.application.port.RegisterUser;
import com.registro.usuarios.domain.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RestController;

/** HTTP de entrada, caso de uso, HTTP de salida. No decide nada: las reglas están en el dominio. */
@RestController
class UserController implements UserApi {

  private static final Logger LOG = LoggerFactory.getLogger(UserController.class);

  private final RegisterUser registerUser;

  UserController(RegisterUser registerUser) {
    this.registerUser = registerUser;
  }

  @Override
  public UserResponse register(RegisterUserRequest request) {
    User user = registerUser.register(UserWebMapper.toCommand(request));
    // La única línea de éxito: el identificador y la dirección enmascarada, nunca un secreto.
    LOG.info("User registered: id={}, email={}", user.id().value(), user.email().masked());
    return UserWebMapper.toResponse(user);
  }
}
