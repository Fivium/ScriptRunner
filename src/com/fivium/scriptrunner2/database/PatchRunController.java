package com.fivium.scriptrunner2.database;


import com.fivium.scriptrunner2.Logger;
import com.fivium.scriptrunner2.PatchScript;
import com.fivium.scriptrunner2.ScriptRunner;
import com.fivium.scriptrunner2.database.sql.SQLManager;
import com.fivium.scriptrunner2.ex.ExFatalError;
import com.fivium.scriptrunner2.script.ScriptExecutable;
import com.fivium.scriptrunner2.script.ScriptSQL;

import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;

import java.math.BigDecimal;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;

import java.util.Map;


/**
 * Controller for the logging of an individual patch run.
 */
public class PatchRunController {
  
  private final DatabaseConnection mDatabaseConnection;  
  private final PromotionController mPromotionController;
  private final PatchScript mPatchScript;
  private final Writer mLogWriter;
  
  protected String mPreviousHash = "";
  
  private boolean mIsRerun = false;  
  private int mPatchRunId;
  
  /**
   * Constructs a new PatchRunController for validating and logging the execution of a PatchScript.
   * @param pScriptRunner Current ScriptRunner.
   * @param pPatchScript PatchScript to be executed.
   */
  public PatchRunController(ScriptRunner pScriptRunner, PatchScript pPatchScript){
    this(pScriptRunner.getDatabaseConnection(), pScriptRunner.getPromotionController(), pPatchScript);
  }
  
  /**
   * Constructs a new PatchRunController for validating and logging the execution of a PatchScript.
   * @param pDatabaseConnection Database connection to use when logging the patch.
   * @param pPromotionController Promotion controller for the overall promotion which this patch is being run in.
   * @param pPatchScript PatchScript to be executed.
   */
  public PatchRunController(DatabaseConnection pDatabaseConnection, PromotionController pPromotionController, PatchScript pPatchScript){
    mDatabaseConnection = pDatabaseConnection;    
    mPromotionController = pPromotionController;
    mPatchScript = pPatchScript;
    mLogWriter = new StringWriter();
  }
  
  /**
   * Tests if the patch is allowed to run, based on the presence of any non-ignored previous patch runs in the run table.
   * @param pConnection Connection to use.
   * @return True if the patch is allowed to run, false otherwise.
   */
  protected boolean validatePatchRun(Connection pConnection){    
    Map<String,Object> lResultMap;
    try {
      lResultMap = SQLManager.queryMap(pConnection, SQLManager.SQL_FILE_SELECT_PATCH_RUN_COUNT, 
                                       mPatchScript.getPatchLabel(), mPatchScript.getPatchNumber());
    }
    catch (SQLException e) {
      throw new ExFatalError("Error querying status for patch script " + mPatchScript.getDisplayName());
    }
    
    //Check if at least 1 row was founrd
    if(lResultMap.get("IGNORED_COUNT") != null){    
      //Set the re-run flag to true if any ignore runs exist
      mIsRerun = ((BigDecimal) lResultMap.get("IGNORED_COUNT")).intValue() > 0;
      
      mPreviousHash = (String) lResultMap.get("LAST_FILE_HASH");
      
      //Return true if there are 0 non-ignored previous runs
      return ((BigDecimal) lResultMap.get("NOT_IGNORED_COUNT")).intValue() == 0;
    }
    else {
      //No existing rows - this won't be a re-run, and is allowed
      mIsRerun = false;
      return true;
    }
  }
  
  /**
   * Tests if the patch is allowed to run and logs the start of the run if it is.
   * @return True if the run was started, false otherwise.
   */
  public boolean validateAndStartPatchRun(){
    
    Connection lConnection = mDatabaseConnection.getLoggingConnection();
    
    //Check if this patch has already been run
    if(!validatePatchRun(lConnection)){      
      return false;
    }
    
    //Insert the new record
    insertPatchRun();
    
    //Add a new logger so we can get the log for this individual patch run
    Logger.addLogWriter(mLogWriter);
    
    return true;    
  }
  
  /**
   * Inserts a row into the patch_runs table.
   */
  private void insertPatchRun() {
    try {
      Connection lConnection = mDatabaseConnection.getLoggingConnection();    
      CallableStatement lStatement = lConnection.prepareCall(SQLManager.getSQLByName(SQLManager.SQL_FILE_INSERT_PATCH_RUN));
      
      lStatement.setString("patch_label", mPatchScript.getPatchLabel());
      lStatement.setInt   ("patch_number", mPatchScript.getPatchNumber());
      lStatement.setString("description", mPatchScript.getDescription());    
      lStatement.setInt   ("promotion_id", mPromotionController.getPromotionRunId());
      lStatement.setString("promotion_label", mPromotionController.getPromotionLabel());
      lStatement.setInt   ("load_seq",  mPatchScript.getPromotionSequencePosition());
      lStatement.setString("hash",  mPatchScript.getPatchFileHash());
      lStatement.setString("version",  mPatchScript.getFileVersion());
      lStatement.setClob  ("file",  new StringReader(mPatchScript.getOriginalPatchString()));
      
      lStatement.registerOutParameter("patch_id", Types.INTEGER);
      
      lStatement.executeUpdate();
            
      lConnection.commit();    
      
      mPatchRunId = lStatement.getInt("patch_id");
      
      lStatement.close();
    }
    catch (SQLException e) {
      throw new ExFatalError("Failed to insert patch run", e);
    }
  }
  
  /**
   * Finalises a patch run by updating the corresponding log table row.
   * @param pWasSuccess True if this is logging a successful run or false if it failed.
   */
  public void endPatchRun(boolean pWasSuccess) {
    try {
      Connection lConnection = mDatabaseConnection.getLoggingConnection();    
      CallableStatement lStatement = lConnection.prepareCall(SQLManager.getSQLByName(SQLManager.SQL_FILE_UPDATE_PATCH_RUN));
      
      lStatement.setString("status", pWasSuccess ? "COMPLETE" : "FAILED");
      lStatement.setClob("log", new StringReader(mLogWriter.toString()));
      lStatement.setInt("id", mPatchRunId);
      
      lStatement.executeUpdate();
      lStatement.close();
      
      lConnection.commit();
    }
    catch (SQLException e) {
      throw new ExFatalError("Failed to end patch run", e);
    }
    finally {
      Logger.removeLogWriter(mLogWriter);
    }
  }
  
  /**
   * Tests if an individual statement within this patch is allowed to run.
   * @param pScriptSQL Statement to validate.
   * @return True if the statement is allowed to run, false otherwise.
   * @throws SQLException If the validation cannot be performed.
   */
  protected boolean validateScriptExecutable(ScriptSQL pScriptSQL) 
  throws SQLException {
    
    boolean lRunAllowed;
    if(mIsRerun){
      //If this is a script re-run, check the statement table to see if we can run this statement
      int lRowCount = SQLManager.queryScalarInt(mDatabaseConnection.getLoggingConnection(), SQLManager.SQL_FILE_SELECT_PATCH_RUN_STATEMENT_COUNT, 
                                                mPatchScript.getPatchLabel(), mPatchScript.getPatchNumber(), pScriptSQL.getHash());
      lRunAllowed = lRowCount == 0;
    }
    else {
      //Don't bother checking if this script has not been run before
      lRunAllowed = true;
    }
    
    return lRunAllowed;
  }
  
  /**
   * Tests if an individual statement within this patch is allowed to run and logs the start of the run if it is (for SQL statements).
   * @param pScriptExecutable Statement to validate.
   * @param pStatementSequence Sequence of the statement within the patch script for logging purposes.
   * @return True if the statement is allowed to be executed.
   */
  public boolean validateAndStartScriptExecutable(ScriptExecutable pScriptExecutable, int pStatementSequence) {    
    try {    
      if(pScriptExecutable instanceof ScriptSQL){
        ScriptSQL lScriptSQL = (ScriptSQL) pScriptExecutable;
        
        //Check if we can run this exectuable
        boolean lRunAllowed = validateScriptExecutable(lScriptSQL);
        
        if(lRunAllowed) {
          //Do the insert
          insertPatchRunStatement(lScriptSQL, pStatementSequence);
        }
        
        return lRunAllowed;
      }
      else {
        //Allow all other types of script executable
        return true;
      }    
    }
    catch (SQLException e) {
      throw new ExFatalError("Failed to validate script executable", e);
    }
  }
  
  /**
   * Inserts a row into the patch_run_statements table.
   * @param pScriptSQL Statement being logged.
   * @param pStatementSequence Sequence of the statement within the patch script.
   * @throws SQLException If logging fails.
   */
  private void insertPatchRunStatement(ScriptSQL pScriptSQL, int pStatementSequence) 
  throws SQLException {    
    Connection lConnection = mDatabaseConnection.getLoggingConnection();    
    CallableStatement lStatement = lConnection.prepareCall(SQLManager.getSQLByName(SQLManager.SQL_FILE_INSERT_PATCH_RUN_STATEMENT));
    
    lStatement.setString("hash", pScriptSQL.getHash());    
    lStatement.setString("patch_label", mPatchScript.getPatchLabel());
    lStatement.setInt   ("patch_number", mPatchScript.getPatchNumber());    
    lStatement.setInt   ("patch_run_id", mPatchRunId);        
    lStatement.setInt   ("script_seq", pStatementSequence);
    lStatement.setClob  ("sql", new StringReader(pScriptSQL.getParsedSQL()));    
    
    lStatement.executeUpdate();    
    lStatement.close();
    
    lConnection.commit(); 
  }
  
  /**
   * Finalises the log row of an individual statement execution.
   * @param pScriptExecutable Statement being logged.
   * @param pWasSuccess True if the execution was successful, false otherwise.
   */
  public void endPatchRunStatement(ScriptExecutable pScriptExecutable, boolean pWasSuccess) {    
    try {    
      if(pScriptExecutable instanceof ScriptSQL){
        ScriptSQL lScriptSQL = (ScriptSQL) pScriptExecutable;
      
        Connection lConnection = mDatabaseConnection.getLoggingConnection();    
        CallableStatement lStatement = lConnection.prepareCall(SQLManager.getSQLByName(SQLManager.SQL_FILE_UPDATE_PATCH_RUN_STATEMENT));
        
        lStatement.setString("status", pWasSuccess ? "COMPLETE" : "FAILED");
        lStatement.setInt   ("patch_run_id", mPatchRunId);    
        lStatement.setString("hash", lScriptSQL.getHash());     
        
        lStatement.executeUpdate();
        lStatement.close();
        
        lConnection.commit(); 
      }
    }
    catch (SQLException e) {
      throw new ExFatalError("Failed to end patch run statement", e);
    }    
  }

  /**
   * Gets the database connection being used to control this patch run.
   * @return Database connection.
   */
  protected DatabaseConnection getDatabaseConnection() {
    return mDatabaseConnection;
  }

  /**
   * Gets the PatchScript which this controller is logging.
   * @return PatchScript.
   */
  protected PatchScript getPatchScript() {
    return mPatchScript;
  }

  /**
   * Tests if this patch has been run before.
   * @return True if this is a re-run.
   */
  protected boolean isRerun() {
    return mIsRerun;
  }
  
  /**
   * Tests if this controller is for -noexec executions, which it is not.
   * @return False.
   */
  public boolean isNoExecController(){
    return false;
  }
}
