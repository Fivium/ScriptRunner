package com.fivium.scriptrunner2.script;


import com.fivium.scriptrunner2.database.DatabaseConnection;

import java.sql.SQLException;

/**
 * Implementation of a COMMIT statement within a patch script.
 */
class ScriptCommit 
implements ScriptExecutable {

  /**
   * Issues a commit on the current connection.
   * @param pDatabaseConnection Current database connection.
   * @throws SQLException If commit the fails.
   */
  @Override
  public void execute(DatabaseConnection pDatabaseConnection) 
  throws SQLException {    
    pDatabaseConnection.getPromoteConnection().commit();
  }

  @Override
  public String getDisplayString() {
    return "COMMIT";
  }

}
