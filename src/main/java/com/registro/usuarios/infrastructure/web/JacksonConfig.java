package com.registro.usuarios.infrastructure.web;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.type.LogicalType;

/**
 * By default Jackson turns {@code "name": 123} into the string {@code "123"}. The contract says a
 * non-string scalar for a string field is a wrongly typed body, so the coercion of numbers and
 * booleans into text is switched off for every textual property, phone fields included.
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
