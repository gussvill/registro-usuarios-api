package com.registro.usuarios.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.registro.usuarios.domain.exception.InvalidUserDataException;
import com.registro.usuarios.domain.exception.Reason;
import java.util.Set;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

/**
 * Utilidad de pruebas que ejecuta una acción que se espera rechazada y devuelve los motivos
 * tipados.
 */
public final class Rejections {

  private Rejections() {}

  public static Set<Reason> reasonsOf(ThrowingCallable action) {
    Throwable thrown = catchThrowable(action);
    assertThat(thrown).isInstanceOf(InvalidUserDataException.class);
    return ((InvalidUserDataException) thrown).reasons();
  }
}
