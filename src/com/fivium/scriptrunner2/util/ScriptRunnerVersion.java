package com.fivium.scriptrunner2.util;


import com.fivium.scriptrunner2.PatchScript;
import com.fivium.scriptrunner2.database.sql.SQLManager;
import com.fivium.scriptrunner2.ex.ExFatalError;
import com.fivium.scriptrunner2.install.Installer;
import com.fivium.scriptrunner2.update.Updater;

import java.io.IOException;

import java.sql.Connection;
import java.sql.SQLException;

import java.util.List;
import java.util.Properties;


/**
 * Utility class for determining the version of ScriptRunner currently in use.
 */
public class ScriptRunnerVersion {
  private ScriptRunnerVersion() {}
  
  private static final String VERSION_FILE_NAME = "version.properties";
  private static final String VERSION_PROPERTY = "version_number";
  private static final String SOFTWARE_NAME_PROPERTY = "software_name";
  
  private static Properties gProperties = null;
  
  /**
   * Gets the parsed Properties object for the version.properties file, parsing it if it has not already been parsed.
   * @return Version properties file.
   */
  private static Properties getOrInitProperties(){
    if(gProperties == null){
      try {
        gProperties = new Properties();
        gProperties.load(ScriptRunnerVersion.class.getResourceAsStream(VERSION_FILE_NAME));      
      }
      catch (IOException e) {
        throw new ExFatalError("Version file not found", e);
      }
    }
    return gProperties;
  }
  
  /**
   * Gets the current version number of ScriptRunner as specified in the version file.  This file should be maintained 
   * by the build process (i.e. Ant).
   * @return Current ScriptRunner version number as a string.
   */
  public static String getVersionNumber(){
    String lVersionFileString;
    
    Properties lProps = getOrInitProperties();
    lVersionFileString = lProps.getProperty(VERSION_PROPERTY);    
    
    if(lVersionFileString == null) {
      throw new ExFatalError("Version file in incorrect format");
    }
    
    return lVersionFileString;
  }
  
  /**
   * Get the full version string, i.e. the current software name concatenated with the current version number,
   * as defined in the version properties file.
   * @return Version string.
   */
  public static String getVersionString(){
    return getOrInitProperties().getProperty(SOFTWARE_NAME_PROPERTY, "ScriptRunner") + " version " +  getVersionNumber();
  }
  
  /**
   * Gets the latest update patch number (i.e. the latest PATCHSCRIPTRUNNER patch) which this version of ScriptRunner
   * is expecting to have been run. This will be checked against the target database. This number should reflect the 
   * latest update patch in the update package.
   * @return Patch number of the latest ScriptRunner install patch which this version is expecting.
   */
  public static int getLatestExpectedUpdatePatchNumber(){
    List<PatchScript> lList = Updater.getUpdatePatches();
    return lList.get(lList.size()-1).getPatchNumber();
  }
  
  public static int getLatestUpdatePatchNumber(Connection pConnection){
    try {
      return SQLManager.queryScalarInt(pConnection, SQLManager.SQL_FILE_VERSION_CHECK, Installer.INSTALL_PATCH_PREFIX);
    }
    catch (SQLException e) {
      throw new ExFatalError("Error getting latest update patch number: " + e.getMessage(), e);
    }
  }
  
}
