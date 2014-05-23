package com.fivium.scriptrunner2.database;


import com.fivium.scriptrunner2.CommandLineOption;
import com.fivium.scriptrunner2.CommandLineWrapper;
import com.fivium.scriptrunner2.Logger;
import com.fivium.scriptrunner2.ex.ExFatalError;
import com.fivium.scriptrunner2.ex.ExPromote;
import com.fivium.scriptrunner2.install.Installer;
import com.fivium.scriptrunner2.util.ScriptRunnerVersion;
import com.fivium.scriptrunner2.util.XFUtil;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;

import java.util.Properties;

import oracle.jdbc.OracleDriver;
import oracle.jdbc.driver.OracleConnection;


/**
 * Provider for two underlying JDBC connections - the promote connection, which should be used to promote files, and the
 * logging connection, which should be used to log the promotion's progress. The promote connection can be switched to
 * connect as different users if required, or as SYSDBA using the special "CONNECT SYSDBA" connection markup. <br/><br/>
 *
 * It is the consumer's responsibility to close both connections before the application exits.
 */
public class DatabaseConnection {  
  
  private static final String GRANT_PROXY_GRANTEE_BIND =  ":grantee";
  private static final String GRANT_PROXY_PROMOTEUSER_BIND =  ":promoteuser";
  private static final String GRANT_PROXY_SQL =  "ALTER USER " + GRANT_PROXY_GRANTEE_BIND + " GRANT CONNECT THROUGH " + GRANT_PROXY_PROMOTEUSER_BIND;
  
  private static final String JDBC_PREFIX =  "jdbc:oracle:thin:@";
  
  public static final String DEFAULT_PROMOTE_USER = "PROMOTEMGR";
  
  public static final String SYSDBA_USER = "SYSDBA";
  
  private final OracleConnection mPromoteConnection;
  private OracleConnection mSysDBAPromoteConnection = null;
  private final OracleConnection mLoggingConnection;
  
  /** Name of the initial user being used to perform the promote (i.e. PROMOTEMGR) */
  private final String mPromoteUserName;
  
  private final String mJDBCConnectString;
  private final String mPromoteUserPassword;
  
  private boolean mIsProxyConnectionActive = false;
  private boolean mIsSysDBAConnectionActive = false;
  private String mProxyUserName = "";
    
  /**
   * Establishes a JDBC connection string from the various combinations of arguments that can be provided to the ScriptRunner
   * command line. In order of precedence, these are:
   * <ol>
   * <li>A fully-specified JDBC connection string</li>
   * <li>Individual arguments for host, port and SID or service name (SID takes priority if both are specified)</li>
   * <li>Prompted stdin input for host, port and SID</li>
   * </ol>
   * @param pCommandLine Command line options.
   * @return A JDBC connection string.
   */
  private static String establishConnectionString(CommandLineWrapper pCommandLine){
    
    String lConnectionString;
    String lCmdLineJDBC = pCommandLine.getOption(CommandLineOption.JDBC_CONNECT_STRING);
    if(!XFUtil.isNull(lCmdLineJDBC)){
      //1) if specified in full, use that
      lConnectionString = lCmdLineJDBC;
    }
    else {
      //2) if not specified, look for individual arguments
      String lHostName = pCommandLine.getOption(CommandLineOption.DB_HOST);
      String lPort = pCommandLine.getOption(CommandLineOption.DB_PORT);
      String lSID = pCommandLine.getOption(CommandLineOption.DB_SID);
      String lServiceName = pCommandLine.getOption(CommandLineOption.DB_SERVICE_NAME);
      
      //3) If still not specified, prompt user
      if(XFUtil.isNull(lHostName)){
        lHostName = CommandLineWrapper.readArg("Enter database hostname", false);
      }
      
      if(XFUtil.isNull(lPort)){
        lPort = CommandLineWrapper.readArg("Enter database port", false);
      }
      
      if(XFUtil.isNull(lSID) && XFUtil.isNull(lServiceName)){
        lSID = CommandLineWrapper.readArg("Enter database SID (for service name, specify -service argument)", false);
      }      
      
      if(!XFUtil.isNull(lSID)){
        //Construct SID connect syntax if a SID was specified
        lConnectionString = JDBC_PREFIX + lHostName + ":" + lPort + ":" + lSID;
      }
      else {
        //Otherwise construct service name connect syntax
        lConnectionString = JDBC_PREFIX + "//" + lHostName + ":" + lPort + "/" + lServiceName;
      }
      
    }   
    
    return lConnectionString;
  }
  
  /**
   * Establishes a connection to the database using the command line options provided. Note that these connections should
   * be cleaned up after use.
   * @param pCommandLine CommandLine containing all required arguments.
   * @return A DatabaseConnection wrapper object which contains active JDBC connections.
   * @throws ExPromote If the connection fails.
   */
  public static DatabaseConnection createConnection(CommandLineWrapper pCommandLine) 
  throws ExPromote {
    return createConnection(pCommandLine, pCommandLine.hasOption(CommandLineOption.DB_SYSDBA), true, true);
  }
  
  /**
   * Establishes a connection to the database using the command line options provided. Note that these connections should
   * be cleaned up after use.
   * @param pCommandLine CommandLine containing all required arguments.
   * @param pCheckVersion If true, asserts that the latest ScriptRunner patch to be run is expected by this version of ScriptRunner.
   * @return A DatabaseConnection wrapper object which contains active JDBC connections.
   * @throws ExPromote If the connection fails.
   */
  public static DatabaseConnection createConnection(CommandLineWrapper pCommandLine, boolean pCheckVersion) 
  throws ExPromote {
    return createConnection(pCommandLine, pCommandLine.hasOption(CommandLineOption.DB_SYSDBA), true, pCheckVersion);
  }
  
  /**
   * Establishes a connection to the database using the command line options provided. Note that these connections should
   * be cleaned up after use.
   * @param pCommandLine CommandLine containing all required arguments.
   * @param pConnectAsSysDBA Set to true if a SYSDBA promotion connection is required.
   * @param pCreateLoggingConnection Set to true if a logging connection is required.
   * @param pCheckVersion If true, asserts that the latest ScriptRunner patch to be run is expected by this version of ScriptRunner.
   * @return A DatabaseConnection wrapper object which contains active JDBC connections.
   * @throws ExPromote If the connection fails.
   */
  public static DatabaseConnection createConnection(CommandLineWrapper pCommandLine, boolean pConnectAsSysDBA, boolean pCreateLoggingConnection, boolean pCheckVersion) 
  throws ExPromote {
    
    //Establish a connection string    
    String lConnectionString = establishConnectionString(pCommandLine);
    
    //Establish username and password
    String lOverridePromoteUser = pCommandLine.getOption(CommandLineOption.PROMOTE_USER);
    //Connect as a specified user if specified, or as PROMOTEMGR by default
    String lPromoteUser = (XFUtil.isNull(lOverridePromoteUser) ? DEFAULT_PROMOTE_USER : lOverridePromoteUser).toUpperCase();
    
    String lPassword = pCommandLine.getOption(CommandLineOption.PROMOTE_PASSWORD);    
    if(XFUtil.isNull(lPassword)){
      lPassword = CommandLineWrapper.readPassword("Enter password for " + lPromoteUser);
    }
    
    Logger.logDebug("Connecting to database using JDBC connect string " + lConnectionString);
    
    //Create the connections
    
    OracleConnection lPromoteConnection;
    try {
      lPromoteConnection = createOracleConnection(lConnectionString, lPromoteUser, lPassword, pConnectAsSysDBA);      
    }
    catch (SQLException e) {
      throw new ExPromote("Error establishing database connection (promotion connection): " + e.getMessage(), e);
    }
    
    OracleConnection lLoggingConnection = null;
    if(pCreateLoggingConnection){
      try {
        //never connect as SYSDBA for logging
        lLoggingConnection = createOracleConnection(lConnectionString, lPromoteUser, lPassword, false);        
      }
      catch (SQLException e) { 
        throw new ExPromote("Error establishing database connection (logging connection): " + e.getMessage(), e);
      }
    }
    
    if(pCheckVersion){
      //Perform the ScriptRunner version check
      int lLatestPatch = ScriptRunnerVersion.getLatestUpdatePatchNumber(lLoggingConnection);
      int lExpectedPatch = ScriptRunnerVersion.getLatestExpectedUpdatePatchNumber();      
      
      //Check the latest patch number on the database matches the expected patch number in this build      
      if(lLatestPatch != lExpectedPatch){
        throw new ExPromote("ScriptRunner database version check failed - the latest " + Installer.INSTALL_PATCH_PREFIX + " patch run was #" + 
                            lLatestPatch + " but this version expects the latest patch to be #" + lExpectedPatch + ". Use -update to update the database.");
      }
    }
    
    return new DatabaseConnection(lPromoteConnection, lLoggingConnection, lPromoteUser, lConnectionString, lPassword);
  }
  
  /**
   * Creates a new OracleConnection using the given parameters. The new connection will have auto commit disabled.
   * @param pConnectionString JDBC string to connect with.
   * @param pUser User to connect as.
   * @param pPassword Password for user.
   * @param pConnectAsSysDBA If true, establishes a SYSDBA connection. If false a standard connection is created.
   * @return The new OracleConnection.
   * @throws ExFatalError If the JDBC connect syntax is invalid.
   * @throws SQLException If the connection fails for any other reason.
   */
  private static OracleConnection createOracleConnection(String pConnectionString, String pUser, String pPassword, boolean pConnectAsSysDBA) 
  throws SQLException {
    
    Properties lProperties = new Properties();
    OracleDriver lDriver = new OracleDriver();
    
    lProperties.setProperty("user", pUser);
    lProperties.setProperty("password", pPassword);
    if(pConnectAsSysDBA){
      //If a SYSDBA connection is required set this property 
      lProperties.setProperty("internal_logon", "sysdba");
      Logger.logDebug("Connecting as SYSDBA");
    }
    
    OracleConnection lConnection = (OracleConnection) lDriver.connect(pConnectionString, lProperties);
    
    //The connect method seems to return null if the JDBC string is invalid
    if(lConnection == null){
      throw new ExFatalError("Could not connect to database. Check your JDBC string syntax: '" + pConnectionString + "'");
    }
    
    //Auto commit should be off by default
    lConnection.setAutoCommit(false);
    
    return lConnection;    
  }
  
  private DatabaseConnection(OracleConnection pPromoteConnection, OracleConnection pLoggingConnection, String pUsername, String pJDBCConnectString, String pPromoteUserPassword){        
    mPromoteConnection = pPromoteConnection;
    mLoggingConnection = pLoggingConnection;
    mPromoteUserName = pUsername.toUpperCase();
    mJDBCConnectString = pJDBCConnectString;
    mPromoteUserPassword = pPromoteUserPassword;
  }

  /**
   * Gets the JDBC connection which should be used to perform file promotions. PatchScripts and Loaders have the ability
   * to modify the currently connected user as required.
   * @return Promotion JDBC connection.
   */
  public Connection getPromoteConnection() {
    return mIsSysDBAConnectionActive ? mSysDBAPromoteConnection : mPromoteConnection;
  }
  
  /**
   * Gets the JDBC connection which is being used to log the promotion process. This should always be connected as the 
   * promote user and never as SYSDBA. This may be null if it was not requested at construction time.
   * @return Logging JDBC connection.
   */
  public Connection getLoggingConnection() {
    return mLoggingConnection;
  }  
  
  /**
   * Closes the promote connection. Any outstanding transactions are rolled back and a warning is logged.
   */
  public void closePromoteConnection(){
    try {      
      //Rollback any outstanding transactions - there shouldn't be any, so this is probably an internal mistake
      //If this rollback isn't performed, closing the connection issues a commit which is potentially dangerous
      if(isTransactionActive(mPromoteConnection)){
        mPromoteConnection.rollback();
        Logger.logWarning("Uncommitted data detected on promote connection - rolling back");
      }      
      mPromoteConnection.close();
      
      if(mSysDBAPromoteConnection != null){
        //Clean up the SYSDBA connection if it was created 
        if(isTransactionActive(mSysDBAPromoteConnection)){
          mSysDBAPromoteConnection.rollback();
          Logger.logWarning("Uncommitted data detected on promote SYSDBA connection - rolling back");
        } 
        
        mSysDBAPromoteConnection.close();
      }
    }
    catch (SQLException e) {
      throw new ExFatalError("Error when disconnecting from database promote connection", e);
    }
  }
  
  /**
   * Closes the logging connection. Any outstanding transactions are committed.
   */
  public void closeLoggingConnection(){
    if(mLoggingConnection != null){
      try { 
        mLoggingConnection.close();
      }
      catch (SQLException e) {
        throw new ExFatalError("Error when disconnecting from database logging connection", e);
      }
    }
  }
  
  /**
   * Tests if there is currently a database transaction active on the given connection.
   * @param pOnConnection Connection to test.
   * @return True if a transaction is active, false otherwise.
   */
  private boolean isTransactionActive(OracleConnection pOnConnection){
    CallableStatement lStatement;
    try {
      lStatement = pOnConnection.prepareCall("{?= call DBMS_TRANSACTION.LOCAL_TRANSACTION_ID()}");
      lStatement.registerOutParameter(1, Types.VARCHAR);
      lStatement.execute();
      String lTransactionId = lStatement.getString(1);
      lStatement.close();
      
      //If there is a transaction ID, then a transaction is active      
      return !XFUtil.isNull(lTransactionId);
    }
    catch (SQLException e) {
      throw new ExFatalError("Error when checking transaction status", e);
    }
  }
  
  /**
   * Tests if there is currently a database transaction active on the current promotion connection.
   * @return True if a transaction is active, false otherwise.
   */
  public boolean isTransactionActive(){
    return isTransactionActive(mIsSysDBAConnectionActive ? mSysDBAPromoteConnection : mPromoteConnection);
  }
  
  /**
   * Grants the proxy connect privilege to the given user so they can proxy in via the promotion user (i.e. PROMOTEMGR)
   * @param pGranteeUser User to grant privilege to.
   */
  private void grantProxyConnectToUser(String pGranteeUser){
    String lGrantSQL = GRANT_PROXY_SQL.replace(GRANT_PROXY_GRANTEE_BIND, pGranteeUser).replace(GRANT_PROXY_PROMOTEUSER_BIND, mPromoteUserName);
    try {
      mPromoteConnection.prepareStatement(lGrantSQL).execute();      
    }
    catch (SQLException e) {
      throw new ExFatalError("Failed to grant proxy connect to user " + pGranteeUser, e);
    }
  }
  
  /**
   * Rolls back the current promotion connection, supressing any errors (they will be logged as warnings).
   */
  public void safelyRollback(){
    try {
      getPromoteConnection().rollback();
    }
    catch (SQLException e) {
      Logger.logWarning("Error rolling back promotion connection: " + e.getMessage());
    }
  }
  
  /**
   * Switches this connection so it belongs to the given user. If the connection is already connected as this user, no action
   * is taken. There is a special user name of "SYSDBA" which switches the promotion connection from a standard connection to a
   * SYSDBA connection but remains connected as PROMOTEMGR. If a transaction is active when the switch is requested, an error
   * is raised.
   * @param pUsername User to connect as.
   * @throws SQLException If the database cannot perform this operation.
   */
  public void switchUser(String pUsername) 
  throws SQLException {
    
    pUsername = pUsername.toUpperCase();
    
    //Validate not attempting to CONNECT as the main user
    if(mPromoteUserName.equals(pUsername)){
      throw new ExFatalError("Illegal attempt to CONNECT as promotion control user " + pUsername + "; use DISCONNECT instead");
    }
    
    //If we're already connected as the requested user, short-circuit out
    if(mProxyUserName.equals(pUsername) || (SYSDBA_USER.equals(pUsername) && mIsSysDBAConnectionActive)){
      Logger.logDebug("Connect as " + pUsername + " requested but already connected as that user");
      return;
    }
    
    //Validate that there is no active transaction before attempting to CONNECT
    if(isTransactionActive()){
      safelyRollback();
      throw new ExFatalError("Attempted to switch user from " + currentUserName() + " to " + pUsername + " but a transaction is still active");
    }
    
    //Disconnect from the current proxy user first
    if(mIsProxyConnectionActive || mIsSysDBAConnectionActive){
      disconnectProxyUser();
    }
    
    if(SYSDBA_USER.equals(pUsername)){
      //Switch the connection to be SYSDBA
      //Create a connection just in time if necessary
      if(mSysDBAPromoteConnection == null){
        mSysDBAPromoteConnection = createOracleConnection(mJDBCConnectString, mPromoteUserName, mPromoteUserPassword, true);
      }
      mIsSysDBAConnectionActive = true;
    }
    else {
      //Switch to a proxy connection
      
      //Allow the target user to connect through the promotion user
      grantProxyConnectToUser(pUsername);
      
      //Switch the session
      Properties lProps = new Properties();
      lProps.put(OracleConnection.PROXY_USER_NAME, pUsername);
      mPromoteConnection.openProxySession(OracleConnection.PROXYTYPE_USER_NAME, lProps);
      
      mProxyUserName = pUsername;
      mIsProxyConnectionActive = true;
    }    
    
  }
  
  /**
   * Disconnects the currently connected user, returning the connection to its original state (i.e. connected as the 
   * original promotion user). If the connection is already in its original state, no action is taken.
   * @throws ExFatalError If a transaction is still active when the disconnect is requested.
   * @throws SQLException If the database cannot perform this operation.   
   */
  public void disconnectProxyUser() 
  throws SQLException {
    
    if(mIsProxyConnectionActive || mIsSysDBAConnectionActive){
      if(isTransactionActive()){
        safelyRollback();
        throw new ExFatalError("Attempted to disconnect from user " + currentUserName() + " but a transaction is still active");
      }
      else {
        if(mIsProxyConnectionActive){
          //Close the proxy connection
          mPromoteConnection.close(OracleConnection.PROXY_SESSION);
          
          mProxyUserName = "";
          mIsProxyConnectionActive = false;
        }
        else if (mIsSysDBAConnectionActive) {
          mIsSysDBAConnectionActive = false;
        }
      }
    }
    else {
      //Do nothing if not a proxy at the moment
      Logger.logDebug("Disconnect requested but no proxy connection active");
    }
  }
  
  /**
   * Gets the name of the user the promotion connection is currently connected as.
   * @return Current user name.
   */
  public String currentUserName(){
    return mIsProxyConnectionActive ? mProxyUserName : mPromoteUserName + (mIsSysDBAConnectionActive ? "/SYSDBA" : "");
  }

  /**
   * Tests if a proxy connection, either as another user or as SYSDBA, is active.
   * @return True if a proxy connection is active.
   */
  public boolean isProxyConnectionActive() {
    return mIsProxyConnectionActive || mIsSysDBAConnectionActive;
  }

  /**
   * Gets the JDBC connection string used to establish this database connection. This may have been directly provided by
   * the user or been constructed by prompting the user for the relevant details.
   * @return JDBC Connection string.
   */
  public String getJDBCConnectString() {
    return mJDBCConnectString;
  }
}
