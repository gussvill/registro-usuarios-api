package com.registro.usuarios.infrastructure.web;

import com.registro.usuarios.application.RegisterUserUseCase;
import org.springframework.web.bind.annotation.RestController;

/** HTTP in, use case, HTTP out. It decides nothing: the rules are in the domain. */
@RestController
class UserController implements UserApi {

  private final RegisterUserUseCase registerUser;

  UserController(RegisterUserUseCase registerUser) {
    this.registerUser = registerUser;
  }

  @Override
  public UserResponse register(RegisterUserRequest request) {
    return UserWebMapper.toResponse(registerUser.register(UserWebMapper.toCommand(request)));
  }
}
