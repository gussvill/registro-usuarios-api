package com.registro.usuarios.infrastructure.web;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.type.LogicalType;

/**
 * Por defecto Jackson convierte {@code "name": 123} en la cadena {@code "123"}. El contrato dice
 * que un escalar que no es cadena en un campo de texto es un cuerpo con tipo incorrecto, por lo que
 * se desactiva la coerción de números y booleanos a texto en toda propiedad textual, incluidos los
 * campos de teléfono.
 */
@Configuration(proxyBeanMethods = false)
class JacksonConfig {

  @Bean
  JsonMapperBuilderCustomizer strictStringTyping() {
    return builder ->
        builder.withCoercionConfig(
            LogicalType.Textual,
            coercion ->
                coercion
                    .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                    .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                    .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail));
  }
}
