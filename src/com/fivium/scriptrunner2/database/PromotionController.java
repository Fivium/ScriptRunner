package com.fivium.scriptrunner2.database;


import com.fivium.scriptrunner2.Logger;
import com.fivium.scriptrunner2.PromotionFile;
import com.fivium.scriptrunner2.database.sql.SQLManager;
import com.fivium.scriptrunner2.ex.ExFatalError;
import com.fivium.scriptrunner2.ex.ExInternal;
import com.fivium.scriptrunner2.ex.ExPromote;
import com.fivium.scriptrunner2.util.ScriptRunnerVersion;

import java.math.BigDecimal;

import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;

import java.util.Map;


/**
 * Provides methods for starting and finishing a promote, including the promotion of PromotionFiles (not PatchScripts).
 * Holds stateful information about the current promote's internal and external IDs.
 */
public class PromotionController {
  
  private final String mPromotionLabel;
  private final DatabaseConnection mDatabaseConnection;
  
  private int mPromotionRunId;
  
  private long mStartTimeMS;
  
  
  /**
   * Constructs a new PromotionController for controlling the promotion of the given label.
   * @param pDatabaseConnection Database connection to use for logging.
   * @param pPromotionLabel Promotion label to be promoted.
   */
  public PromotionController(DatabaseConnection pDatabaseConnection, String pPromotionLabel){
    mDatabaseConnection = pDatabaseConnection;
    mPromotionLabel = pPromotionLabel;    
  }
  
  /**
   * Tests if this label is allowed to be promoted by checking the relevant database tables.
   * @param pConnection Database connection.
   * @return True if the promotion is allowed.
   */
  protected boolean checkPromotionAllowed(Connection pConnection ){
    int lExistingPromotionCount;
    try {
      lExistingPromotionCount = SQLManager.queryScalarInt(pConnection, SQLManager.SQL_FILE_SELECT_PROMOTION_RUN_COUNT, mPromotionLabel);
    }
    catch (SQLException e) {
      throw new ExInternal("Error running promotion check SQL", e);
    }
    return lExistingPromotionCount == 0;
  }
  
  /**
   * Validates that a promote can begin and logs the start on the database if it can.
   * @return True if the promote can be started.
   * @throws ExPromote If the promote cannot be started, as this is an error which needs to be handeled.
   * This could be because the promotion label has already been run and is not 'ignored'.
   */
  public boolean startPromote()
  throws ExPromote {
    
    mStartTimeMS = System.currentTimeMillis();
    
    Connection lConnection = mDatabaseConnection.getLoggingConnection();
    
    //Check if this promote has already been run at least once and no ignore flag has been set    
    if(!checkPromotionAllowed(lConnection)){
      throw new ExPromote("This label has already been promoted. To force a re-promotion, use the ignore flag.");
    }
    
    //Insert the new promotion run record
    try {
      insertPromotionRunRow(lConnection);
    }
    catch (SQLException e) {
      throw new ExInternal("Error running insert promotion row SQL", e);
    }
        
    Logger.logInfo("\n*** Starting promote\n");
    Logger.logAndEcho("Promoting files...");
    
    return true;
  }
  
  /**
   * Inserts a row into the promotion_runs table.
   * @param pConnection Connection to use.
   * @throws SQLException If logging fails.
   */
  private void insertPromotionRunRow(Connection pConnection) 
  throws SQLException {
    //Prepare the call
    String lStatementString = SQLManager.getSQLByName(SQLManager.SQL_FILE_INSERT_PROMOTION_RUN);    
    CallableStatement lStatement = pConnection.prepareCall(lStatementString);
    
    //Set params
    lStatement.setString("promotion_label", mPromotionLabel);
    lStatement.setString("version", ScriptRunnerVersion.getVersionNumber());
    lStatement.registerOutParameter("new_id", Types.INTEGER);
    
    //Execute and commit
    lStatement.executeUpdate();
       
    mPromotionRunId = lStatement.getInt("new_id");
    
    lStatement.close();
    
    pConnection.commit();
  }
  
  /**
   * Marks the promote as finished on the database and closes the database logging connection.
   * @param pWasSuccess If true, the promote will be marked as successful. Otherwise it will be marked as a failure.
   * @throws SQLException If a database error occurs.
   */
  public void endPromote(boolean pWasSuccess) 
  throws SQLException {
    
    Clob lClob;
    Connection lConnection = mDatabaseConnection.getLoggingConnection();
    
    String lStatementString = SQLManager.getSQLByName(SQLManager.SQL_FILE_UPDATE_PROMOTION_RUN);    
    CallableStatement lStatement;    

    lStatement = lConnection.prepareCall(lStatementString);
    
    lStatement.setString("status", pWasSuccess ? "COMPLETE" : "FAILED");
    lStatement.setInt("id", mPromotionRunId);
    lStatement.registerOutParameter("log", Types.CLOB);
    
    lStatement.executeUpdate();
        
    lClob = lStatement.getClob("log");
    
    if(lClob == null){
      //This can happen if the promote did not start properly
      Logger.logDebug("Promote row CLOB was null");
    }
    else {
      Logger.writeLogToClob(lClob);  
    }
    
    lStatement.close();
    
    lConnection.commit();
    mDatabaseConnection.closeLoggingConnection();
    
    Logger.logInfo("\n*** Promotion finished in " + (System.currentTimeMillis() - mStartTimeMS) / 1000  + " seconds\n");
    
  }
  
  /**
   * Internal property object for passing around the result of querying the promotion_files table.
   */
  protected class FilePromoteStatus {
    /** True if the file is allowed to be promoted. */
    final boolean mIsPromoteAllowed;
    /** True if the file has been promoted before. */
    final boolean mIsRerun;
    /** The file hash of the last instance of the file to be promoted. Can be empty. */
    final String mPreviousHash;
    
    FilePromoteStatus(boolean pIsPromoteAllowed, boolean pIsRerun, String pPreviousHash){
      mIsPromoteAllowed = pIsPromoteAllowed;
      mIsRerun = pIsRerun;
      mPreviousHash = pPreviousHash;
    }
  }
  
  /**
   * Gets a FilePromoteStatus property object for the given file, using the given connection. This shows if a file is 
   * allowed to be promoted, if it will be a re-run, etc.
   * @param pPromotionFile File to validate.
   * @param pConnection Connection to be used.
   * @return True if the promote is allowed.
   */
  protected FilePromoteStatus getFilePromoteStatus(PromotionFile pPromotionFile, Connection pConnection){
    Map<String,Object> lResultMap;
    boolean lIsPromoteAllowed;
    boolean lIsRerun;
    String lPreviousHash;
    try {
      lResultMap = SQLManager.queryMap(pConnection, SQLManager.SQL_FILE_SELECT_PROMOTION_FILE_COUNT, 
                                       mPromotionLabel, pPromotionFile.getFilePath(), pPromotionFile.getFileIndex());
    }
    catch (SQLException e) {
      throw new ExFatalError("Error querying status for file " + pPromotionFile.getFilePath());
    }    
    
    //Check if at least 1 row was founrd
    if(lResultMap.get("IGNORED_COUNT") != null){    
      //Set the re-run flag to true if any ignore runs exist
      lIsRerun = ((BigDecimal) lResultMap.get("IGNORED_COUNT")).intValue() > 0;  
      
      //Return true if there are 0 non-ignored previous runs
      lIsPromoteAllowed = ((BigDecimal) lResultMap.get("NOT_IGNORED_COUNT")).intValue() == 0;
      
      lPreviousHash = (String) lResultMap.get("LAST_FILE_HASH");
    }
    else {
      //No existing rows - this won't be a re-run, and is allowed
      lIsRerun = false;
      lIsPromoteAllowed = true;
      lPreviousHash = "";
    }
    
    return new FilePromoteStatus(lIsPromoteAllowed, lIsRerun, lPreviousHash);    
  }
  
  /**
   * Validates that a file is allowed to be promoted as part of the current promotion and inserts a log row entry if it
   * is.
   * @param pPromotionFile File to be promoted.
   * @return True if the promotion is allowed, false otherwise.
   */
  public boolean validateAndStartFilePromote(PromotionFile pPromotionFile){
    
    Connection lConnection = mDatabaseConnection.getLoggingConnection();
    
    ///Check the file has not already been promoted    
    if(!getFilePromoteStatus(pPromotionFile, lConnection).mIsPromoteAllowed){
      //This file has already been promoted in this promote      
      return false;
    }
    
    //Insert the new record
    int lRunFileId = insertPromotionRunFile(lConnection, pPromotionFile);
    pPromotionFile.setPromotionFileId(lRunFileId);
    
    return true;
  }
  
  /**
   * Inserts a new row into the <tt>promotion_files</tt> table and returns the new row's ID column.
   * @param pConnection Connection to use.
   * @param pPromotionFile File to log.
   * @return New ID.
   */
  private int insertPromotionRunFile(Connection pConnection, PromotionFile pPromotionFile){
    String lStatementString = SQLManager.getSQLByName(SQLManager.SQL_FILE_INSERT_PROMOTION_FILE);    
    CallableStatement lStatement;    
    try {
      lStatement = pConnection.prepareCall(lStatementString);
      
      lStatement.setInt("run_id", mPromotionRunId);
      lStatement.setString("label", mPromotionLabel);
      lStatement.setString("path", pPromotionFile.getFilePath());
      lStatement.setInt("sequence", pPromotionFile.getSequencePosition());
      lStatement.setString("loader", pPromotionFile.getLoaderName());
      lStatement.setString("hash", pPromotionFile.getFileHash());
      lStatement.setString("version", pPromotionFile.getFileVersion());
      lStatement.setInt("index", pPromotionFile.getFileIndex());
      
      lStatement.registerOutParameter("new_id", Types.INTEGER);
      
      lStatement.executeUpdate();
      pConnection.commit();
      
      int lNewId = lStatement.getInt("new_id");
      lStatement.close();
      
      return lNewId;
    }
    catch (SQLException e) {
      throw new ExFatalError("Error inserting promotion run file row for file " + pPromotionFile.getFilePath(), e);
    }
  }
  
  /**
   * Finalises the log row for a file promote.
   * @param pPromotionFile File which has been promoted.
   * @param pWasSuccess True if the promotion was successful, false otherwise.
   */
  public void finaliseFilePromote(PromotionFile pPromotionFile, boolean pWasSuccess){
    try {
      Connection lConnection = mDatabaseConnection.getLoggingConnection();
      SQLManager.executeUpdate(lConnection, SQLManager.SQL_FILE_UPDATE_PROMOTION_FILE, pWasSuccess ? "COMPLETE" : "FAILED", pPromotionFile.getPromotionFileId());
      lConnection.commit();
    }
    catch (SQLException e) {
      throw new ExFatalError("Error updating promotion run file row for file " + pPromotionFile.getFilePath(), e);
    }
  }
  
  /**
   * Tests if this is controller is running a -noexec promotion, which it is not.
   * @return False.
   */
  public boolean isNoExecController(){
    return false;
  }

  /**
   * Gets the promotion label which this controller is currently promoting.
   * @return Promotion label.
   */
  public String getPromotionLabel() {
    return mPromotionLabel;
  }

  /**
   * Gets the ID of the promotion_runs log row for this promotion.
   * @return Run ID.
   */
  public int getPromotionRunId() {
    return mPromotionRunId;
  }

  /**
   * Gets the current database connection for this controller.
   * @return Database connection.
   */
  protected DatabaseConnection getDatabaseConnection() {
    return mDatabaseConnection;
  }

}
