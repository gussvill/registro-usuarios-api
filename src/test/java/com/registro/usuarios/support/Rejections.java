package com.registro.usuarios.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.registro.usuarios.domain.exception.InvalidUserDataException;
import com.registro.usuarios.domain.model.Reason;
import java.util.Set;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

/** Test helper that runs an action expected to be rejected and returns the typed reasons. */
public final class Rejections {

  private Rejections() {}

  public static Set<Reason> reasonsOf(ThrowingCallable action) {
    Throwable thrown = catchThrowable(action);
    assertThat(thrown).isInstanceOf(InvalidUserDataException.class);
    return ((InvalidUserDataException) thrown).reasons();
  }
}
