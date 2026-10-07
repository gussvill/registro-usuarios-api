package com.registro.usuarios.infrastructure.web;

import com.registro.usuarios.application.RegisterUserUseCase;
import com.registro.usuarios.domain.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RestController;

/** HTTP in, use case, HTTP out. It decides nothing: the rules are in the domain. */
@RestController
class UserController implements UserApi {

  private static final Logger LOG = LoggerFactory.getLogger(UserController.class);

  private final RegisterUserUseCase registerUser;

  UserController(RegisterUserUseCase registerUser) {
    this.registerUser = registerUser;
  }

  @Override
  public UserResponse register(RegisterUserRequest request) {
    User user = registerUser.register(UserWebMapper.toCommand(request));
    // The only success line: the identifier and the masked address, never a secret.
    LOG.info("User registered: id={}, email={}", user.id().value(), user.email().masked());
    return UserWebMapper.toResponse(user);
  }
}
