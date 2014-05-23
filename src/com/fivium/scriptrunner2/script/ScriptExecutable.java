package com.fivium.scriptrunner2.script;


import com.fivium.scriptrunner2.database.DatabaseConnection;

import java.sql.SQLException;

/**
 * Interface for any statement in a script which needs to be executed. This could be a SQL statement or script control command
 * (e.g. CONNECT, DISCONNECT, etc). <tt>ScriptExectuable</tt>s are created by parsing script strings using methods on
 * the {@link ScriptExecutableParser}class.
 */
public interface ScriptExecutable {
  
  /**
   * Executes this command on the database, using the provided connection. Some commands may alter the state of the
   * connection object.
   * @param pDatabaseConnection Database connection to operate on.
   * @throws SQLException If an error occurs on the database.
   */
  public void execute(DatabaseConnection pDatabaseConnection) throws SQLException;

  /**
   * Gets a string representation of this command for diagnostic or reporting purposes.
   * @return
   */
  public String getDisplayString();
}
