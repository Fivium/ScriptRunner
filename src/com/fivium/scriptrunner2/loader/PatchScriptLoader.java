package com.fivium.scriptrunner2.loader;


import com.fivium.scriptrunner2.Logger;
import com.fivium.scriptrunner2.PatchScript;
import com.fivium.scriptrunner2.PromotionFile;
import com.fivium.scriptrunner2.ScriptRunner;
import com.fivium.scriptrunner2.database.DatabaseConnection;
import com.fivium.scriptrunner2.database.PatchRunController;
import com.fivium.scriptrunner2.database.UnloggedPatchRunController;
import com.fivium.scriptrunner2.ex.ExFatalError;
import com.fivium.scriptrunner2.ex.ExParser;
import com.fivium.scriptrunner2.ex.ExPromote;
import com.fivium.scriptrunner2.script.ScriptExecutable;
import com.fivium.scriptrunner2.script.ScriptSQL;

import java.io.IOException;


/**
 * This loader is used to run PatchScripts on the target database. PatchScripts typically contain a combination of
 * DDL for creating database objects and DML for patching data. Executions are logged in special log tables.
 * A PatchScript should only be executed once on a target database, unless the <tt>ignore_flag</tt> mechanism is used to
 * explicitly override this behaviour. If a script fails for any reason, it should be "resumable" from the point it
 * failed, i.e. only unexecuted statements are executed on the second run. This loader object handles all of these use cases.
 */
public class PatchScriptLoader 
extends BuiltInLoader {  
  
  /**
   * Directly runs a PatchScript from a file. Patches should be pre-parsed before running to assert there are no
   * errors in the promote. The {@link #runPatchScript} method should be used to run pre-parsed PatchScripts.
   * @param pScriptRunner Current ScriptRunner.
   * @param pPromotionFile PromotionFile containing the patch to be run.
   * @throws ExPromote If the PatchScript fails.
   */
  @Override
  public void promoteFile(ScriptRunner pScriptRunner, PromotionFile pPromotionFile) 
  throws ExPromote {

    Logger.logDebug("PatchScript promoted directly - should be pre-parsed");

    PatchScript lPatchScript;
    try {
      lPatchScript = PatchScript.createFromPromotionFile(pScriptRunner, pPromotionFile);
    }
    catch (IOException e) {
      throw new ExFatalError("Failed to read Patch " + pPromotionFile.getFilePath(), e);
    }
    catch (ExParser e) {
      throw new ExFatalError("Failed to parse Patch " + pPromotionFile.getFilePath() + ": " + e.getMessage(), e);
    }
    
    runPatchScriptInternal(pScriptRunner, pScriptRunner.getDatabaseConnection(), lPatchScript, false);
  }
  
  /**
   * Runs a PatchScript, coordinating with a PatchRunController to log the execution and control which statements
   * are run within the patch, if any.
   * @param pScriptRunner Current ScriptRunner.
   * @param pPatchScript PatchScript to be executed.
   * @throws ExPromote If the PatchScript fails.
   */
  public void runPatchScript(ScriptRunner pScriptRunner, PatchScript pPatchScript) 
  throws ExPromote {
    runPatchScriptInternal(pScriptRunner, pScriptRunner.getDatabaseConnection(), pPatchScript, false);
  }
  
  /**
   * Runs a PatchScript without any logging or validation.
   * @param pDatabaseConnection DatabaseConnection to run against.
   * @param pPatchScript PatchScript to run.
   * @throws ExPromote If the patch fails.
   */
  public void forceUnloggedRunPatchScript(DatabaseConnection pDatabaseConnection, PatchScript pPatchScript) 
  throws ExPromote {
    runPatchScriptInternal(null, pDatabaseConnection, pPatchScript, true);
  }
  
  /**
   * Runs all statements within a PatchScript, optionally coordinating with a PatchRunController to log the execution 
   * and control which statements are run within the patch, if any.
   * @param pScriptRunner Current ScriptRunner for determining which PatchRunController to use. Can be null.
   * @param pDatabaseConnection Database connection to run the script as.
   * @param pPatchScript PatchScript to be executed.
   * @param pIsUnloggedRun If true, the PatchScript run will not be logged or checked in any way.
   * @throws ExPromote If the PatchScript fails.
   */
  private void runPatchScriptInternal(ScriptRunner pScriptRunner, DatabaseConnection pDatabaseConnection, PatchScript pPatchScript, boolean pIsUnloggedRun) 
  throws ExPromote {

    //Check the patch has not already been run and/or an ignore flag was not set
    PatchRunController lController;
    if (!pIsUnloggedRun) {
      lController = pScriptRunner.createPatchRunController(pPatchScript);
    }
    else {
      lController = new UnloggedPatchRunController();
    }
    boolean lRunPatch = lController.validateAndStartPatchRun();
        
    if(lRunPatch) {    
      //If we're allowed to run this patch
      boolean lPatchSuccess = true;
      int lSequence = 0;
      
      try {    
        Logger.logInfo("Run Patch " + pPatchScript.getDisplayName());
        
        //Loop through each statement in th patch
        for(ScriptExecutable lExecutable : pPatchScript.getExecutableList()){          
          try {
            if(lExecutable instanceof ScriptSQL){
              //Only increment this if it's a SQL statement, so the indexes in the log table reflect the output of the -noexec log
              //(non-SQL statements are not logged)
              lSequence++;
            }
            //Execute the statement (this method checks the statement is runnable)
            runPatchStatement(pDatabaseConnection, lController, pPatchScript, lExecutable, lSequence);            
          }
          catch (Throwable th) {
            //An error occurred when running the statement (or, less likely, logging that it was run)
            lPatchSuccess = false;
            
            //If the patch problem was an ExPromote, assume it's already logged. Otherwise log it. Always rethrow!
            ExPromote lError;
            if(th instanceof ExPromote) {
              lError = (ExPromote) th;
            }
            else {
              lError = new ExPromote("Error executing patch " + pPatchScript.getDisplayName() + ": " + th.getMessage(), th);
              Logger.logError(lError);
            }
            throw lError;
          }        
        }
        
        //Check at the end of patch execution for outstanding transactions
        if(pDatabaseConnection.isTransactionActive()){
          ExPromote lError = new ExPromote("Error executing patch " + pPatchScript.getDisplayName() + ": transaction was still active at end of patch execution");
          Logger.logError(lError);
          //Roll back
          pDatabaseConnection.safelyRollback();          
          throw lError;
        }
        
      }
      finally {
        try {
          //Finalise the patch run
          lController.endPatchRun(lPatchSuccess);
          
          //Disconnect from a proxy connection if required (reset the connection to its original state)
          if(pDatabaseConnection.isProxyConnectionActive()){
            pDatabaseConnection.disconnectProxyUser();            
          }
        
        }
        catch (Throwable th) {
          if(lPatchSuccess){
            throw new ExFatalError("Failed to end patch run for patch " + pPatchScript.getDisplayName() + ": " + th.getMessage(), th);
          }
          else {
            //Don't suppress the original exception with a new exception
            Logger.logInfo("Failed to end patch run for patch " + pPatchScript.getDisplayName() + ": " + th.getMessage());
          }
        }
      }
    }
    else {
      Logger.logInfo("Skip Patch " + pPatchScript.getDisplayName() + " - already run");
    }    
  }
  
  /**
   * Runs a single statement within a PatchScript. This also handles the validation and logging of the statement using
   * the PatchRunController.
   * @param pDatabaseConnection Connection to execute the statement with.
   * @param pPatchRunController PatchRunController containing relating to the PatchScript currently being executed.
   * @param pPatchScript PatchScript being executed.
   * @param pScriptExecutable Script statement to execute.
   * @param pSequence Sequence of the statement within the PatchScript for logging purposes.
   * @throws ExPromote If an error was encountered when running the statement. This error will be written to the current
   * logs so does not need to be re-logged.
   * @throws ExFatalError If an error was encountered when updating metadata. This will NOT be logged and needs to be reported.
   */
  private void runPatchStatement(DatabaseConnection pDatabaseConnection, PatchRunController pPatchRunController, PatchScript pPatchScript, ScriptExecutable pScriptExecutable, int pSequence) 
  throws ExPromote {
        
    //Check the execution is allowed and log the start
    boolean lRunStatement = pPatchRunController.validateAndStartScriptExecutable(pScriptExecutable, pSequence);    
    
    if(lRunStatement) {
      boolean lSuccess = true;
      try {
        long lStart = System.currentTimeMillis();
        Logger.logInfo(pPatchScript.getDisplayName() +  " - Execute (" + pDatabaseConnection.currentUserName() +"):\n" + pScriptExecutable.getDisplayString() + "\n/");
        
        //Run the statement
        pScriptExecutable.execute(pDatabaseConnection);
        long lTimeMS = System.currentTimeMillis() - lStart;
        Logger.logInfo("OK (took " + lTimeMS + " ms)\n");
      }
      catch(Throwable th){
        lSuccess = false;
        Logger.logInfo("SERIOUS ERROR! HALTING PATCH EXECUTION");
        ExPromote lError = new ExPromote("Error executing patch " + pPatchScript.getDisplayName() + ": " + th.getMessage(), th);
        Logger.logError(lError);
        throw lError;
      }
      finally {
        try {
          pPatchRunController.endPatchRunStatement(pScriptExecutable, lSuccess);
        }
        catch (Throwable th){
          if(lSuccess) {
            //Only rethrow if there was not already an error
            throw new ExFatalError("Error logging patch statement result", th);
          }
          else {
            Logger.logInfo("Failed to log patch statement failure: " + th.getMessage());
          }
        }
      }
    }
    else {
      Logger.logInfo("Skipping " + (pPatchRunController.isNoExecController() ? "(-noexec)" : "(already run)") + ":\n" + pScriptExecutable.getDisplayString()  + "\n/\n");
    } 
    
  }
}
