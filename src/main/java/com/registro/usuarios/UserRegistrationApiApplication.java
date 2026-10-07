package com.registro.usuarios;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class UserRegistrationApiApplication {

  public static void main(String[] args) {
    SpringApplication.run(UserRegistrationApiApplication.class, args);
  }
}
