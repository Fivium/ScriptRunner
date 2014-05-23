package com.fivium.scriptrunner2.update;


import com.fivium.scriptrunner2.CommandLineWrapper;
import com.fivium.scriptrunner2.Logger;
import com.fivium.scriptrunner2.PatchScript;
import com.fivium.scriptrunner2.database.DatabaseConnection;
import com.fivium.scriptrunner2.database.PatchRunController;
import com.fivium.scriptrunner2.database.PromotionController;
import com.fivium.scriptrunner2.ex.ExFatalError;
import com.fivium.scriptrunner2.ex.ExParser;
import com.fivium.scriptrunner2.ex.ExPromote;
import com.fivium.scriptrunner2.ex.ExUpdater;
import com.fivium.scriptrunner2.loader.PatchScriptLoader;
import com.fivium.scriptrunner2.util.HashUtil;
import com.fivium.scriptrunner2.util.ScriptRunnerVersion;

import java.io.IOException;

import java.sql.Connection;
import java.sql.SQLException;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.IOUtils;


/**
 * Runs the ScriptRunner update process after an initial installation or when -update is used. This checks the latest
 * ScriptRunner patch to have been run on the target database and runs any subsequent ones.
 */
public class Updater {
  
  //Add files to this array IN CORRECT ORDER
  //Do not add patches with numbers lower than the highest number as they will not be run
  private static final String[] PATCH_FILES = new String[] {
    "PATCHSCRIPTRUNNER000010 (add file_index column to promotion_files table).sql"
  };
  
  private static final String UPDATE_PROMOTION_LABEL_PREFIX = "ScriptRunner-Update-";
  private static final String ALTER_TRIGGER_PREFIX = "ALTER TRIGGER promote_ddl_trigger ";
  
  private final CommandLineWrapper mCommandLineWrapper;
  
  /**
   * Gets all the update PatchScripts from the update package directory, provided they have been declared in the
   * PATCH_FILES static array of this class.
   * @return List of update PatchScripts.
   */
  public static List<PatchScript> getUpdatePatches() {
    List<PatchScript> lPatchList = new ArrayList<PatchScript>();
    
    //Loop through all available update patches
    for(String lPatchFileName : PATCH_FILES){      
      String lUpdatePatchString;
      String lFileHash;    
      //Read file contents
      try {
        lUpdatePatchString = IOUtils.toString(Updater.class.getResourceAsStream(lPatchFileName));
        lFileHash = HashUtil.hashString(lUpdatePatchString);
      }
      catch (IOException e) {
        throw new ExFatalError("Failed to read update patch script " + lPatchFileName, e);
      }
      
      //Parse as a patch script
      PatchScript lPatchScript;
      try {
        lPatchScript = PatchScript.createFromString(lPatchFileName, lUpdatePatchString, lFileHash, "internal");
      }
      catch (ExParser e) {
        throw new ExFatalError("Failed to parse update patch script " + lPatchFileName, e);
      }
      
      lPatchList.add(lPatchScript);
    }
    
    return lPatchList;
  }
  
  /**
   * Runs the update process.
   * @param pCommandLineWrapper Command line arguments.
   * @throws ExUpdater If the update process fails.
   */
  public static void run(CommandLineWrapper pCommandLineWrapper) 
  throws ExUpdater {
    Updater lUpdater = new Updater(pCommandLineWrapper);
    lUpdater.update();
  }
  
  /**
   * Disables the DDL trigger which protects against accidental updates to the ScriptRunner schema.
   * @param pConnection Connection to use.
   * @throws SQLException If trigger could not be disabled.
   */
  private static void disableDDLTrigger(Connection pConnection)
  throws SQLException {
    pConnection.createStatement().execute(ALTER_TRIGGER_PREFIX + "DISABLE");
  }
  
  /**
   * Enables the DDL trigger which protects against accidental updates to the ScriptRunner schema.
   * @param pConnection Connection to use.
   * @throws SQLException If trigger could not be disabled.
   */
  private static void enableDDLTrigger(Connection pConnection)
  throws SQLException {
    pConnection.createStatement().execute(ALTER_TRIGGER_PREFIX + "ENABLE");
  }
  
  private Updater(CommandLineWrapper pCommandLineWrapper) {
    mCommandLineWrapper = pCommandLineWrapper;
  }
  
  /**
   * Performs the update process. Any required patches will be run and logged. If no patches are run, the user is informed
   * via console output.
   * @throws ExUpdater If the update process fails.
   */
  private void update() 
  throws ExUpdater {
    
    DatabaseConnection lDatabaseConnection;
    try {     
      lDatabaseConnection = DatabaseConnection.createConnection(mCommandLineWrapper, false, true, false);
    }
    catch (ExPromote e) {
      throw new ExUpdater("Failed to connect to database: " + e.getMessage(), e);
    }
    
    List<PatchScript> lUpdatePatchList = getUpdatePatches();
    
    int lLatestPatch = ScriptRunnerVersion.getLatestUpdatePatchNumber(lDatabaseConnection.getLoggingConnection());
    
    List<PatchScript> lRunPatchList = new ArrayList<PatchScript>();
    
    //Build up a list of patches to run
    //We cannot use the standard patch checking mechanism as the database may be missing expected columns - so use the number
    //of the latest update patch to be run and promote any patch with a higher number
    for(PatchScript lUpdatePatch : lUpdatePatchList){
      if(lUpdatePatch.getPatchNumber() > lLatestPatch){
        lRunPatchList.add(lUpdatePatch);
      } 
    }    
    
    //Check if any update patches need to be run
    if(lRunPatchList.size() > 0){      
      try {        
        //Disable DDL trigger
        try {
          disableDDLTrigger(lDatabaseConnection.getPromoteConnection());
        }
        catch (SQLException e) {
          throw new ExUpdater("Failed to disable DDL trigger: " + e.getMessage(), e);      
        }
        
        //Run all update scripts in order    
        for(PatchScript lUpdatePatch : lRunPatchList){
          Logger.logAndEcho("Running update patch #" + lUpdatePatch.getPatchNumber());
          //Force the script to run without logging, then log after run (just in case the script is changing the patch tables themselves)
          try {
            new PatchScriptLoader().forceUnloggedRunPatchScript(lDatabaseConnection, lUpdatePatch);
          }
          catch (ExPromote e) {
            throw new ExUpdater("Failed to update due to patch failure: " + e.getMessage(), e);      
          }
        }
      }      
      finally {
        //Re-enable the DDL trigger, ignoring any failure (this should not suppress any original error)
        try {
          enableDDLTrigger(lDatabaseConnection.getPromoteConnection());
        }
        catch (SQLException e) {
          Logger.logInfo("Error: failed to re-enable DDL trigger");
          Logger.logError(e);
        }
      }
    
      //Now log patches which were run
      String lUpdateLabel = UPDATE_PROMOTION_LABEL_PREFIX + ScriptRunnerVersion.getVersionNumber();
      PromotionController lPromotionController = new PromotionController(lDatabaseConnection, lUpdateLabel);
      
      Logger.logInfo("Logging update run");
      
      for(PatchScript lUpdatePatch : lRunPatchList){
        PatchRunController lPatchRunController = new PatchRunController(lDatabaseConnection, lPromotionController, lUpdatePatch);
        try {
          lPromotionController.startPromote();
          lPatchRunController.validateAndStartPatchRun();
          lPatchRunController.endPatchRun(true);        
        }
        catch (ExPromote e) {
          throw new ExUpdater("Failed to log update for patch " + lUpdatePatch.getDisplayName() + ": " + e.getMessage(), e);
        }
      }
      
      //Finalise the update promote
      try {
        lPromotionController.endPromote(true);
      }
      catch (SQLException e) {
        throw new ExUpdater("Failed to log update due to SQL error", e);
      }
    }
    else {
      Logger.logAndEcho("ScriptRunner installation is up to date");
    }
  }  
  
}
