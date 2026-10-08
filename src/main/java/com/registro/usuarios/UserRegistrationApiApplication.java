package com.registro.usuarios;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Punto de entrada de Spring Boot de la API de registro de usuarios. Activa la configuración
 * automática y el escaneo de componentes del paquete base y de las propiedades de configuración. Es
 * la raíz de la arquitectura hexagonal: desde aquí se ensamblan dominio, aplicación e
 * infraestructura.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class UserRegistrationApiApplication {

  public static void main(String[] args) {
    SpringApplication.run(UserRegistrationApiApplication.class, args);
  }
}
