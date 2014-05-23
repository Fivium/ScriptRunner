package com.fivium.scriptrunner2.loader;

import com.fivium.scriptrunner2.PromotionFile;
import com.fivium.scriptrunner2.ScriptRunner;
import com.fivium.scriptrunner2.ex.ExPromote;

/**
 * Special type of loader for running ScriptRunner utility scripts. These are scripts which the user wishes to run
 * during the promote but do not make any structural changes to the database. For instance, a "compile all" script.
 * A singleton UtilLoader is used to just-in-time parse the SQL in the promotion file into a pseuedo MetadataLoader,
 * to allow property binding. The pseudo MetadataLoader (a {@link UtilLoaderScript}) is then executed and discarded.<br/><br/>
 *
 * Utility scripts are always run and their execution is not logged seperately. For this reason end users should ensure
 * that the utility makes no permanent database changes.
 */
public class UtilLoader 
extends BuiltInLoader {
  
  private static UtilLoader INSTANCE = new UtilLoader();
  
  public static UtilLoader getInstance() {
    return INSTANCE;
  }
  
  /**
   * Prevents external construction.
   */
  private UtilLoader(){}  
  
  @Override
  public void promoteFile(ScriptRunner pScriptRunner, PromotionFile pPromotionFile) 
  throws ExPromote {
    
    //Only run anything if this is not a -noexec run
    if(!pScriptRunner.getPromotionController().isNoExecController()){      
      //Create a new UtilLoaderScript for the given promotion file (the promotion file is the util script we want to run)
      UtilLoaderScript lLoader = new UtilLoaderScript(pPromotionFile);
      //Prepare the MetadataLoader - this reads in the SQL and parses for binds
      lLoader.prepare(pScriptRunner);    
      //Directly run the "loader" - i.e. the utility script - with no logging
      lLoader.doPromote(pScriptRunner, pPromotionFile);      
    }
  }
  
  /**
   * A special MetadataLoader which will be used to run a utility script. The contents of the script is treated as a loader -
   * this allows properties to be bound in.
   */
  private static class UtilLoaderScript 
  extends MetadataLoader {
    
    UtilLoaderScript(PromotionFile pPromotionFile) {
      super(BuiltInLoader.LOADER_NAME_SCRIPTRUNNER_UTIL, pPromotionFile.getFilePath());
    }

    /**
     * Disallows the binding of a promotion file (:clob, :blob binds) as this makes no sense for this loader type.
     * @return False.
     */
    @Override
    protected boolean isFileBindingAllowed() {
      return false;
    }

  }
  
}
