package com.fivium.scriptrunner2.loader;

import com.fivium.scriptrunner2.Logger;
import com.fivium.scriptrunner2.PromotionFile;
import com.fivium.scriptrunner2.ScriptRunner;
import com.fivium.scriptrunner2.database.PromotionController;
import com.fivium.scriptrunner2.ex.ExFatalError;
import com.fivium.scriptrunner2.ex.ExPromote;
import com.fivium.scriptrunner2.ex.ExRuntimeRoot;

/**
 * A <tt>SourceLoader</tt> is used to load any database source code or metadata onto the database, excluding DDL which is
 * handled by the {@link PatchScriptLoader}. Database source code typically includes packages, views, triggers, etc.
 * Metadata comprises any files which need to be loaded into tables.
 */
public abstract class SourceLoader 
extends BuiltInLoader{
  
  /**
   * General handler for promoting a DatabaseSource file or database metadata. This method coordinates with a 
   * {@link PromotionController} to handle the execution verification and logging of the promote.
   * @param pScriptRunner Current ScriptRunner.
   * @param pPromotionFile File to be promoted.
   * @throws ExPromote If the promotion fails.
   */
  public void promoteFile(ScriptRunner pScriptRunner, PromotionFile pPromotionFile) 
  throws ExPromote {
    
    PromotionController lController = pScriptRunner.getPromotionController();
    
    boolean lPromoteFile = lController.validateAndStartFilePromote(pPromotionFile);    
    if(lPromoteFile){
      //Do the promotion
      boolean lSuccess = true;
      try {
        doPromote(pScriptRunner, pPromotionFile);
      }
      catch (ExPromote e){
        lSuccess = false;
        handleError(e, pPromotionFile);
        throw e;
      }
      catch (ExRuntimeRoot e) {
        lSuccess = false;
        handleError(e, pPromotionFile);
        throw e;
      }
      catch (Throwable th){
        lSuccess = false;
        ExPromote e = new ExPromote("Unexpected error promoting file " + pPromotionFile.getFilePath(), th);
        handleError(e, pPromotionFile);
        throw e;
      }
      finally {        
        //Log the total time on the database row
        try {
          lController.finaliseFilePromote(pPromotionFile, lSuccess);  
        }
        catch (Throwable th){
          //Only re-throw if there wasn't already an error
          if(lSuccess){
            throw new ExFatalError("Unexpected error finalising promotion log for file " + pPromotionFile.getFilePath(), th);
          }
          else {
            Logger.logInfo("Unexpected error finalising promotion log for file " + pPromotionFile.getFilePath() + ": " + th.getMessage());
          }          
        }
      }      
    }
    else {
      Logger.logInfo("Skipping " + pPromotionFile.getSequencePosition() + ": " + pPromotionFile.getFilePath() + 
                     " - " + (lController.isNoExecController() ? "-noexec promotion" : "already promoted in this label"));
    }
    
  }
  
  private final void handleError(Throwable pError, PromotionFile pPromotionFile){
    Logger.logInfo("SERIOUS ERROR promoting " + pPromotionFile.getLoaderName() + " " + pPromotionFile.getFilePath() + " (manifest position #" + pPromotionFile.getSequencePosition() + "). See below:");
    Logger.logError(pError);
  }
  
  /**
   * Promotes the given PromotionFile using this SourceLoader.
   * @param pScriptRunner Current ScriptRunner.
   * @param pFile File to be promoted.
   * @throws ExPromote If the promotion fails.
   */
  public abstract void doPromote(ScriptRunner pScriptRunner, PromotionFile pFile)
  throws ExPromote;
}
