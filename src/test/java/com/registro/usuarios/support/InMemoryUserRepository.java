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
 * Segundo adaptador de {@link UserRepository}, usado por las pruebas del caso de uso. El agregado
 * es inmutable, así que conservar la instancia equivale a conservar una copia de él.
 *
 * <p>Como el almacenamiento real, rechaza un segundo usuario con el mismo correo, lo que permite a
 * una prueba modelar la carrera en que la comprobación de existencia dice "libre" y luego el insert
 * falla.
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

  /** Lo que se ha guardado, en orden de inserción. */
  public List<User> saved() {
    return new ArrayList<>(users.values());
  }

  public int count() {
    return users.size();
  }
}
