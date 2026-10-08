package com.registro.usuarios.support;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import org.h2.api.Trigger;

/**
 * An H2 trigger that refuses every row and quotes the whole row in the message of its exception,
 * the way a database can quote a rejected value ("value too long for column ..."). Tests install it
 * to prove that such a message never reaches the server log.
 */
public final class QuotingTrigger implements Trigger {

  @Override
  public void fire(Connection connection, Object[] oldRow, Object[] newRow) throws SQLException {
    throw new SQLException("Rejected row, the value was " + Arrays.toString(newRow));
  }
}
