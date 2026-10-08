package com.registro.usuarios.support;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import org.h2.api.Trigger;

/**
 * Un trigger de H2 que rechaza toda fila y cita la fila completa en el mensaje de su excepción,
 * como puede hacerlo una base de datos al citar un valor rechazado ("value too long for column
 * ..."). Las pruebas lo instalan para demostrar que ese mensaje nunca llega al log del servidor.
 */
public final class QuotingTrigger implements Trigger {

  @Override
  public void fire(Connection connection, Object[] oldRow, Object[] newRow) throws SQLException {
    throw new SQLException("Rejected row, the value was " + Arrays.toString(newRow));
  }
}
