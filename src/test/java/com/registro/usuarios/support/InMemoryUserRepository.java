package com.registro.usuarios.support;

import com.registro.usuarios.domain.exception.EmailAlreadyRegisteredException;
import com.registro.usuarios.domain.model.Email;
import com.registro.usuarios.domain.model.User;
import com.registro.usuarios.domain.model.UserId;
import com.registro.usuarios.domain.port.UserRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Second adapter of {@link UserRepository}, used by the use case tests. The aggregate is immutable,
 * so keeping the instance is the same as keeping a copy of it.
 *
 * <p>Like the real storage it refuses a second user with the same email, which lets a test model
 * the race where the existence check says "free" and the insert then fails.
 */
public final class InMemoryUserRepository implements UserRepository {

  private final Map<UserId, User> users = new LinkedHashMap<>();

  @Override
  public boolean existsByEmail(Email email) {
    return users.values().stream().anyMatch(user -> user.email().equals(email));
  }

  @Override
  public void save(User user) {
    if (existsByEmail(user.email())) {
      throw new EmailAlreadyRegisteredException();
    }
    users.put(user.id(), user);
  }

  /** What has been stored, in insertion order. */
  public List<User> saved() {
    return new ArrayList<>(users.values());
  }

  public int count() {
    return users.size();
  }
}
