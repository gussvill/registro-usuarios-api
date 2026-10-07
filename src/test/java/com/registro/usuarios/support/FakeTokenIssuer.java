package com.registro.usuarios.support;

import com.registro.usuarios.domain.model.Email;
import com.registro.usuarios.domain.model.UserId;
import com.registro.usuarios.domain.port.TokenIssuer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Stand-in for the JWT issuer that records what it was asked to sign. */
public final class FakeTokenIssuer implements TokenIssuer {

  /** One call to {@link #issue}. */
  public record Issue(UserId subject, Email email, Instant issuedAt) {}

  private final List<Issue> issues = new ArrayList<>();

  @Override
  public String issue(UserId subject, Email email, Instant issuedAt) {
    issues.add(new Issue(subject, email, issuedAt));
    return "fake-token." + subject.value() + "." + issuedAt.getEpochSecond();
  }

  public List<Issue> issues() {
    return List.copyOf(issues);
  }
}
