package com.fivium.scriptrunner2;


import com.fivium.scriptrunner2.install.Installer;
import com.fivium.scriptrunner2.update.Updater;
import com.fivium.scriptrunner2.util.ScriptRunnerVersion;

import java.io.File;
import java.io.IOException;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import org.apache.commons.cli.ParseException;


/**
 * Main entry point into ScriptRunner from the command line. The JAR manifest should specify this as the Main-Class.
 */
public class Main {
  
  /**
   * ScriptRunner main method.
   * @param args Environment args.
   */
  public static void main(String[] args) {
    
    //Parse command line options    
    CommandLineWrapper lCommandLineOptions = null;
    try {
      lCommandLineOptions = new CommandLineWrapper(args);
    }
    catch (ParseException e) {
      System.err.println("ScriptRunner start failed:");
      System.err.println(e.getMessage());

      CommandLineWrapper.printHelp();
      
      System.exit(1);
    }
    
    //Set up logging
    try {
      File lLogDir;
      if(lCommandLineOptions.hasOption(CommandLineOption.LOG_DIRECTORY)){
        lLogDir = new File(lCommandLineOptions.getOption(CommandLineOption.LOG_DIRECTORY));
      }
      else {
        //Default the log directory to the current working directory
        lLogDir = new File(System.getProperty("user.dir"));
      }
      
      //Create a log file in the specified directory and set it up for writing
      Logger.initialiseLogFile(lLogDir);
    }
    catch (IOException e) {
      System.err.println("ScriptRunner failed to initialise log file:");
      System.err.println(e.getMessage());
      System.exit(1);
    }
    
    //Set up the logger
    if(lCommandLineOptions.hasOption(CommandLineOption.LOG_STANDARD_OUT)){
      Logger.logToStandardOut();
    }
    
    if(lCommandLineOptions.hasOption(CommandLineOption.LOG_DEBUG)){
      Logger.enableDebugLogging();
    }
    
    Logger.logAndEcho(ScriptRunnerVersion.getVersionString());
    
    //Main branch - call the relevant subprocess based on supplied arguments    
    boolean lError = false;  
    try {
      if(lCommandLineOptions.hasOption(CommandLineOption.RUN)){
        ScriptRunner.run(lCommandLineOptions);
        if(lCommandLineOptions.hasOption(CommandLineOption.NO_EXEC)){
          Logger.logAndEcho("-noexec parse completed successfully");   
        }
        else {
          Logger.logAndEcho("Promotion completed successfully");  
        }
      }
      else if(lCommandLineOptions.hasOption(CommandLineOption.BUILD)){
        ScriptBuilder.run(lCommandLineOptions);
        Logger.logAndEcho("Build completed successfully");        
      } 
      else if(lCommandLineOptions.hasOption(CommandLineOption.INSTALL)){
        Logger.logAndEcho("Installing ScriptRunner"); 
        Installer.run(lCommandLineOptions);
        //Haul up to latest version
        Updater.run(lCommandLineOptions);
        Logger.logAndEcho("Install completed successfully");             
      }
      else if(lCommandLineOptions.hasOption(CommandLineOption.UPDATE)){
        Logger.logAndEcho("Checking Scriptrunner is up to date"); 
        Updater.run(lCommandLineOptions);
        Logger.logAndEcho("Update check completed successfully");             
      }
      else if(lCommandLineOptions.hasOption(CommandLineOption.PARSE_SCRIPTS)){
        List<String> lFileList = lCommandLineOptions.getOptionValues(CommandLineOption.PARSE_SCRIPTS);
        Logger.logAndEcho("Parsing " + lFileList.size() + " PatchScript" + (lFileList.size() != 1 ? "s" : "")); 
        lError = !PatchScript.printScriptsToStandardOut(new File(System.getProperty("user.dir")), lFileList);
      } 
      
      //Print a message to standard out if warnings were encountered
      int lWarnCount = Logger.getWarningCount();
      if(Logger.getWarningCount() > 0){
        Logger.logAndEcho(lWarnCount + " warning" + (lWarnCount != 1 ? "s were" : " was") +" detected during execution; please review the log for details");  
      }
    }
    catch (Throwable th){
      String timeStamp = Logger.LOG_FILE_LOG_TIMESTAMP_FORMAT.format(new Date());
      System.err.println("[" + timeStamp + "] Error encountered while running ScriptRunner (see log for details):");
      System.err.println(th.getMessage());
      if(!lCommandLineOptions.hasOption(CommandLineOption.RUN)){
        //Error will already have been logged by runner; for all others log it now
        Logger.logError(th);
      }
      lError = true;        
    }
    finally {      
      Logger.finaliseLogs();
    }    
    
    //Exit with the correct code
    System.exit(lError ? 1 : 0);
  }
}
