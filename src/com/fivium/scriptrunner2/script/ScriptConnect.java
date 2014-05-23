package com.fivium.scriptrunner2.script;


import com.fivium.scriptrunner2.database.DatabaseConnection;

import java.sql.SQLException;

/**
 * Implementation of a CONNECT statement within a patch script.
 */
class ScriptConnect 
implements ScriptExecutable {
    
  private final String mUserName;
  
  /**
   * Construct a new CONNECT statement which will connect as the given user.
   * @param pUserName Name of use to connect as.
   */
  ScriptConnect(String pUserName){
    mUserName = pUserName;
  }
  
  /**
   * Runs this CONNECT command on the current connection.
   * @param pDatabaseConnection Current connection.
   */
  @Override
  public void execute(DatabaseConnection pDatabaseConnection) 
  throws SQLException {
    pDatabaseConnection.switchUser(mUserName);
  }

  @Override
  public String getDisplayString() {
    return "CONNECT " + mUserName;
  }
  
  /**
   * Gets the user name which this CONNECT will connect as.
   * @return User name.
   */
  String getUserName() {
    return mUserName;
  }
}
