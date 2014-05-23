package com.fivium.scriptrunner2.database;


import com.fivium.scriptrunner2.Logger;
import com.fivium.scriptrunner2.PromotionFile;
import com.fivium.scriptrunner2.ScriptRunner;

import java.sql.SQLException;

/**
 * Controller for a promotion which is being run in <tt>-noexec mode</tt>, and therefore will not promote any files.
 */
public class NoExecPromotionController 
extends PromotionController {
  
  private ScriptRunner mScriptRunner;
  
  public NoExecPromotionController(ScriptRunner pScriptRunner, DatabaseConnection pDatabaseConnection, String pPromotionLabel) {
    super(pDatabaseConnection, pPromotionLabel);
    mScriptRunner = pScriptRunner;    
  }

  /**
   * Validates that a promote is allowed to start. In this class, no exception is thrown and the method returns false
   * if the promote is not valid.
   * @return True if    
   */
  @Override
  public boolean startPromote() {
    //Check a non-ignored run does not already exist
    if(!checkPromotionAllowed(getDatabaseConnection().getLoggingConnection())){
      mScriptRunner.addNoExecLabelLog(getPromotionLabel(), false, "Promotion label " + getPromotionLabel() + " already promoted");
      Logger.logAndEcho("-noexec database verification started");
      return false;
    }
    else {      
      return true;
    }
  }

  @Override
  public void endPromote(boolean pWasSuccess) 
  throws SQLException {
    //do nothing
  }

  @Override
  public boolean validateAndStartFilePromote(PromotionFile pPromotionFile) {
    
    FilePromoteStatus lFilePromoteStatus = getFilePromoteStatus(pPromotionFile, getDatabaseConnection().getLoggingConnection());
    
    String lHashDifference = lFilePromoteStatus.mPreviousHash.equals(pPromotionFile.getFileHash()) ? "" : " - PREVIOUS HASH DIFFERENT";
    
    if(!lFilePromoteStatus.mIsPromoteAllowed){      
      mScriptRunner.addNoExecFileLog(pPromotionFile, false, "Already promoted" + lHashDifference);
    }
    else {
      mScriptRunner.addNoExecFileLog(pPromotionFile, true, lFilePromoteStatus.mIsRerun ? "Re-promotion" + lHashDifference : "Initial promotion");
    }
    return false;
  }

  @Override
  public void finaliseFilePromote(PromotionFile pPromotionFile, boolean pWasSuccess) {
    //do nothing
  }
  
  /**
   * Tests if this is controller is running a -noexec promotion, which it is.
   * @return True.
   */
  @Override
  public boolean isNoExecController(){
    return true;
  }
}
