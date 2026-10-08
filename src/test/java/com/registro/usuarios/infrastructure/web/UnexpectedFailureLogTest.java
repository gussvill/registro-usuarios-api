package com.registro.usuarios.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * El texto que registra el manejador de último recurso: clases y frames, nunca un mensaje de
 * excepción.
 */
class UnexpectedFailureLogTest {

  @Test
  void theClassAndTheFramesOfEveryCauseAreKeptAndNoMessageIs() {
    Exception root = new IllegalStateException("root says secret-root-value");
    Exception failure = new RuntimeException("outer says secret-outer-value", root);

    String text = GlobalExceptionHandler.withoutMessages(failure);

    assertThat(text)
        .startsWith("java.lang.RuntimeException")
        .contains("Caused by: java.lang.IllegalStateException")
        .contains("\tat " + UnexpectedFailureLogTest.class.getName())
        .doesNotContain("secret-root-value")
        .doesNotContain("secret-outer-value");
  }

  @Test
  void aFailureWithoutACauseHasNoCausedByBlock() {
    assertThat(GlobalExceptionHandler.withoutMessages(new IllegalArgumentException("x")))
        .doesNotContain("Caused by:");
  }

  @Test
  void aChainThatLoopsBackOnItselfStopsInsteadOfRunningForever() {
    Exception first = new RuntimeException("first");
    Exception second = new RuntimeException("second", first);
    first.initCause(second);

    String text = GlobalExceptionHandler.withoutMessages(first);

    assertThat(text).contains("Caused by: java.lang.RuntimeException");
  }
}
