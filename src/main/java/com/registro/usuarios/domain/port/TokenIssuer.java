package com.registro.usuarios.domain.port;

import com.registro.usuarios.domain.model.Email;
import com.registro.usuarios.domain.model.UserId;
import java.time.Instant;

/**
 * Puerto de salida: emite el token de acceso de un usuario recién registrado. Lo implementa {@code
 * JjwtTokenIssuer}.
 */
public interface TokenIssuer {

  String issue(UserId subject, Email email, Instant issuedAt);
}
