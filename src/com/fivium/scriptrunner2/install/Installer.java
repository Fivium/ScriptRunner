package com.fivium.scriptrunner2.install;


import com.fivium.scriptrunner2.CommandLineOption;
import com.fivium.scriptrunner2.CommandLineWrapper;
import com.fivium.scriptrunner2.Logger;
import com.fivium.scriptrunner2.PatchScript;
import com.fivium.scriptrunner2.database.DatabaseConnection;
import com.fivium.scriptrunner2.database.PatchRunController;
import com.fivium.scriptrunner2.database.PromotionController;
import com.fivium.scriptrunner2.ex.ExInstaller;
import com.fivium.scriptrunner2.ex.ExParser;
import com.fivium.scriptrunner2.ex.ExPromote;
import com.fivium.scriptrunner2.loader.PatchScriptLoader;
import com.fivium.scriptrunner2.util.HashUtil;
import com.fivium.scriptrunner2.util.ScriptRunnerVersion;
import com.fivium.scriptrunner2.util.XFUtil;

import java.io.IOException;

import java.sql.PreparedStatement;
import java.sql.SQLException;

import org.apache.commons.io.IOUtils;


/**
 * Manages the first-run installation of the ScriptRunner metadata tables. This includes creating the promotion user
 * (PROMOTEMGR by default), creating the tables and migrating any legacy PatchScripts to the new format.<br><br>
 *
 * <strong>IMPORTANT:</strong> You should not make changes to the installation files or the methods in this class.
 * ScriptRunner installation is a one-time event which cannot be re-run. Changes should be added as updates instead.
 */
public class Installer {
  
  private final CommandLineWrapper mCommandLineWrapper;
  
  public static final String INSTALL_PATCH_PREFIX = "PATCHSCRIPTRUNNER";
  
  private static final String INSTALL_PATCH_NAME = INSTALL_PATCH_PREFIX + "00000 (ScriptRunner install).sql" ;  
  private static final String INSTALL_PROMOTION_LABEL = "ScriptRunner-Install";
  
  private static final String CREATE_USER_FILE_NAME = "create_user.sql";
  private static final String SET_PERMISSIONS_FILE_NAME = "set_permissions.sql";
  private static final String CREATE_OBJECTS_FILE_NAME = "create_objects.sql";
  private static final String MIGRATE_PATCHES_FILE_NAME = "migrate_patches.sql";
  private static final String PROMOTE_USER_BIND_STRING = ":PROMOTEUSER";  
  private static final String INSTALL_LABEL_BIND_STRING = ":INSTALL_LABEL";  
  
  /**
   * Installs ScriptRunner to a database, using the command line options provided to control the process.
   * @param pCommandLineWrapper Command line options.
   * @throws ExInstaller If installation fails.
   */
  public static void run(CommandLineWrapper pCommandLineWrapper)
  throws ExInstaller {
    Installer lInstaller = new Installer(pCommandLineWrapper);
    lInstaller.install();
  }
  
  private Installer(CommandLineWrapper pCommandLineWrapper){
    mCommandLineWrapper = pCommandLineWrapper;    
  }
  
  /**
   * Performs the ScriptRunner installation.
   * @throws ExInstaller If installation fails.
   */
  private void install() 
  throws ExInstaller {
    //Establish a SYS connection
    DatabaseConnection lDatabaseConnection;
    try {
      if(!mCommandLineWrapper.hasOption(CommandLineOption.PROMOTE_USER)){
        mCommandLineWrapper.overrideOption(CommandLineOption.PROMOTE_USER, "SYS");
      }
      lDatabaseConnection = DatabaseConnection.createConnection(mCommandLineWrapper, true, false, false);
    }
    catch (ExPromote e) {
      throw new ExInstaller("Failed to connect to database: " + e.getMessage(), e);
    }
    
    //Prompt user for promote user name
    String lPromoteUserName = mCommandLineWrapper.getOption(CommandLineOption.INSTALL_PROMOTE_USER);
    lPromoteUserName = XFUtil.nvl(lPromoteUserName, DatabaseConnection.DEFAULT_PROMOTE_USER).toUpperCase();

    String lArgPassword = null;

    if (mCommandLineWrapper.hasOption(CommandLineOption.INSTALL_PROMOTE_PASSWORD)){
      lArgPassword = mCommandLineWrapper.getOption(CommandLineOption.INSTALL_PROMOTE_PASSWORD);
    }
    
    //Create the new promote user
    String lNewPassword = createUser(lDatabaseConnection, lPromoteUserName, lArgPassword);
    
    Logger.logAndEcho("Setting user privileges...");
    
    //Run the initial install patch for issuing grants to the user
    runInstallPatch(lDatabaseConnection, lPromoteUserName, SET_PERMISSIONS_FILE_NAME);
        
    //Close existing connections
    lDatabaseConnection.closePromoteConnection();
    lDatabaseConnection.closeLoggingConnection();
    
    //RECONNECT as the new promote user
    mCommandLineWrapper.overrideOption(CommandLineOption.PROMOTE_USER, lPromoteUserName);
    mCommandLineWrapper.overrideOption(CommandLineOption.PROMOTE_PASSWORD, lNewPassword); 
    //Always use the JDBC string from the previous connection to avoid re-prompting the user for details
    mCommandLineWrapper.overrideOption(CommandLineOption.JDBC_CONNECT_STRING, lDatabaseConnection.getJDBCConnectString());
    try {
      lDatabaseConnection = DatabaseConnection.createConnection(mCommandLineWrapper, false, true, false);
    }
    catch (ExPromote e) {
      throw new ExInstaller("Failed to connect to database: " + e.getMessage(), e);
    }

    Logger.logAndEcho("Creating database objects..."); 
    
    //Run part 2
    PatchScript lInstallPatch = runInstallPatch(lDatabaseConnection, lPromoteUserName, CREATE_OBJECTS_FILE_NAME);
    
    //Log the success in the patch tables we just created
    PromotionController lPromotionController = new PromotionController(lDatabaseConnection, INSTALL_PROMOTION_LABEL);
    PatchRunController lPatchRunController = new PatchRunController(lDatabaseConnection, lPromotionController, lInstallPatch);
    try {
      lPromotionController.startPromote();
      lPatchRunController.validateAndStartPatchRun();
      lPatchRunController.endPatchRun(true);
      lPromotionController.endPromote(true);
    }
    catch (ExPromote e) {
      throw new ExInstaller("Failed to log installation", e);
    }
    catch (SQLException e) {
      throw new ExInstaller("Failed to log installation", e);
    }
        
    Logger.logAndEcho("Migrating legacy patches..."); 
    
    //Migrate patches but do not allow failure to propogate
    try {
      runInstallPatch(lDatabaseConnection, lPromoteUserName, MIGRATE_PATCHES_FILE_NAME);
    }
    catch (Throwable th){
      Logger.logAndEcho("Migration of legacy patches failed; see log for details");
      Logger.logError(th);
    }
    
    lDatabaseConnection.closePromoteConnection();
    lDatabaseConnection.closeLoggingConnection();    
  }
  
  /**
   * Creates the promotion user.
   * @param pDatabaseConnection Connection to use to create user (should be SYSDBA).
   * @param pPromoteUserName Name of new promotion user.
   * @param pPassword The password passed in to the command line. If XFUtil.isNull, then it'll be prompted
   * @return The password of the new user.
   * @throws ExInstaller If the user cannot be created.
   */
  private String createUser(DatabaseConnection pDatabaseConnection, String pPromoteUserName, String pPassword)
  throws ExInstaller {

    String lPassword = pPassword;
    //Prompt user for password
    if (XFUtil.isNull(pPassword) ){
      lPassword = CommandLineWrapper.readPassword("Enter password for new " + pPromoteUserName + " user");
      String lConfirmPassword = CommandLineWrapper.readPassword("Confirm password");

      if(!lPassword.equals(lConfirmPassword)){
        throw new ExInstaller("Passwords did not match");
      }
    }
    
    String lCreateUserSQL;
    try {
      lCreateUserSQL = IOUtils.toString(getClass().getResourceAsStream(CREATE_USER_FILE_NAME));
    }
    catch (IOException e) {
      throw new ExInstaller("Could not read user create script", e);
    }
    //Replace the username and password in the create user SQL file
    lCreateUserSQL = lCreateUserSQL.replaceAll(PROMOTE_USER_BIND_STRING, pPromoteUserName);
    lCreateUserSQL = lCreateUserSQL.replaceAll(":PASSWORD", lPassword);
    
            
    Logger.logAndEcho("Creating new user...");

    //Run the SQL to create the userdev
    PreparedStatement lCreateUserStatement;
    try {
      lCreateUserStatement = pDatabaseConnection.getPromoteConnection().prepareStatement(lCreateUserSQL);
      lCreateUserStatement.executeUpdate();
      lCreateUserStatement.close();
    }
    catch (SQLException e) {
      throw new ExInstaller("Failed to create " + pPromoteUserName + " user: " + e.getMessage(), e);
    } 
    
    return lPassword;
  }
  
  /**
   * Runs one of the built-in installation patches in this package.
   * @param pDatabaseConnection Connection to run the patch on.
   * @param pPromoteUserName Username of the promote user.
   * @param pPatchName Name of the patch SQL file in the install package.
   * @return The PatchScript which was created in order to run the patch.
   * @throws ExInstaller
   */
  private PatchScript runInstallPatch(DatabaseConnection pDatabaseConnection, String pPromoteUserName, String pPatchName) 
  throws ExInstaller {
    
    String lInstallPatchString;
    String lFileHash;
    try {
      lInstallPatchString = IOUtils.toString(getClass().getResourceAsStream(pPatchName));
      lFileHash = HashUtil.hashString(lInstallPatchString);
    }
    catch (IOException e) {
      throw new ExInstaller("Failed to read install patch script", e);
    }
    
    lInstallPatchString = lInstallPatchString.replaceAll(PROMOTE_USER_BIND_STRING, pPromoteUserName);
    lInstallPatchString = lInstallPatchString.replaceAll(INSTALL_LABEL_BIND_STRING, INSTALL_PROMOTION_LABEL);
        
    PatchScript lInstallPatchScript;
    try {
      lInstallPatchScript = PatchScript.createFromString(INSTALL_PATCH_NAME, lInstallPatchString, lFileHash, ScriptRunnerVersion.getVersionString());
    }
    catch (ExParser e) {
      throw new ExInstaller("Failed to parse install file as a PatchScript", e);
    }
    
    PatchScriptLoader lPatchScriptLoader = new PatchScriptLoader();
    try {
      lPatchScriptLoader.forceUnloggedRunPatchScript(pDatabaseConnection, lInstallPatchScript);
    }
    catch (ExPromote e) {
      throw new ExInstaller("Failed to install ScriptRunner: " + e.getMessage(), e);
    }
    
    return lInstallPatchScript;
  }
    
}
