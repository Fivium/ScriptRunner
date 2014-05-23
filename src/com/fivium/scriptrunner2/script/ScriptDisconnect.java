package com.fivium.scriptrunner2.script;


import com.fivium.scriptrunner2.database.DatabaseConnection;

import java.sql.SQLException;

/**
 * Implementation of a DISCONNECT statement within a patch script.
 */
class ScriptDisconnect 
implements ScriptExecutable {

  public void execute(DatabaseConnection pDatabaseConnection) 
  throws SQLException {
    pDatabaseConnection.disconnectProxyUser();
  }

  public String getDisplayString() {
    return "DISCONNECT";
  }

}
