package com.registro.usuarios.domain.port;

import com.registro.usuarios.domain.model.Email;
import com.registro.usuarios.domain.model.UserId;
import java.time.Instant;

/** Outbound port: issues the access token of a newly registered user. */
public interface TokenIssuer {

  String issue(UserId subject, Email email, Instant issuedAt);
}
