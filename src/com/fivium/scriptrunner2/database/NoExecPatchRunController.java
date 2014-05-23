package com.fivium.scriptrunner2.database;


import com.fivium.scriptrunner2.PatchScript;
import com.fivium.scriptrunner2.ScriptRunner;
import com.fivium.scriptrunner2.ex.ExFatalError;
import com.fivium.scriptrunner2.script.ScriptExecutable;
import com.fivium.scriptrunner2.script.ScriptSQL;

import java.sql.SQLException;

/**
 * Controller for a promotion which is being run in <tt>-noexec</tt> mode, and therefore will not run any patches.
 */
public class NoExecPatchRunController 
extends PatchRunController {
  
  private final ScriptRunner mScriptRunner;
  
  public NoExecPatchRunController(ScriptRunner pScriptRunner, PatchScript pPatchScript) {
    super(pScriptRunner, pPatchScript);
    mScriptRunner = pScriptRunner;
  }

  @Override
  public boolean validateAndStartPatchRun() {
    
    //Warn about hash differences
    boolean lCanRun = validatePatchRun(getDatabaseConnection().getLoggingConnection());
    String lHashDifference = getPatchScript().getPatchFileHash().equals(mPreviousHash) ? "" : " - PREVIOUS HASH DIFFERENT";
    
    if(lCanRun){
      mScriptRunner.addNoExecPatchLog(getPatchScript(), true, isRerun() ? "Re-run; see below" + lHashDifference : "Full run");      
      //Allow patch to "run" so individual statements are executed
      return true;
    }
    else {
      mScriptRunner.addNoExecPatchLog(getPatchScript(), false, "Already run" + lHashDifference);
      //Do not allow patch to "run" as no statements will be executed
      return false;
    }    
  }

  @Override
  public boolean validateAndStartScriptExecutable(ScriptExecutable pScriptExecutable, int pStatementSequence) {
    
    if(pScriptExecutable instanceof ScriptSQL && isRerun()){
      try {
        ScriptSQL lSQL = (ScriptSQL) pScriptExecutable;
        boolean lRunAllowed = validateScriptExecutable(lSQL);
        
        mScriptRunner.addNoExecPatchStatementLog(lSQL, lRunAllowed);
        
      }
      catch (SQLException e) {
        throw new ExFatalError("Failed when validating script executable", e);
      }
    }
        
    //AWLAYS RETURN FALSE HERE - otherwise statement will actually run
    return false;    
  }  

  @Override
  public void endPatchRun(boolean pWasSuccess) {
    //Do nothing
  }

  @Override
  public void endPatchRunStatement(ScriptExecutable pScriptExecutable, boolean pWasSuccess) {
    //Do nothing
  }

  /**
   * Tests if this controller is for -noexec executions, which it is.
   * @return True.
   */
  @Override
  public boolean isNoExecController() {
    return true;
  }
}
