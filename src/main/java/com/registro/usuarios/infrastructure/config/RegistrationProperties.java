package com.registro.usuarios.infrastructure.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Registration rules that are operator configuration, bound from {@code app.registration.*}. Both
 * are regular expressions: the email pattern is applied to the lower-cased address and the password
 * pattern to the password as received. Lengths are checked before either pattern runs.
 *
 * @param emailPattern format of an email address
 * @param passwordPattern format of a password
 */
@ConfigurationProperties(prefix = "app.registration")
@Validated
public record RegistrationProperties(
    @NotBlank String emailPattern, @NotBlank String passwordPattern) {}
