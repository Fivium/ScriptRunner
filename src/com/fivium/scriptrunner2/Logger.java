package com.fivium.scriptrunner2;


import com.fivium.scriptrunner2.ex.ExInternal;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;

import java.sql.Clob;
import java.sql.SQLException;

import java.text.DateFormat;
import java.text.SimpleDateFormat;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.commons.io.IOUtils;


/**
 * Provider of a simple logging interface for ScriptRunner. Multiple log destinations are supported and there is basic support
 * for different logging levels.
 */
public class Logger {
  
  private static final List<Writer> gLogWriterList = new ArrayList<Writer>();
  private static boolean gLogToStandardOut = false;
  private static boolean gLogFileInitialised = false;
  
  private static final String LOG_FILE_NAME_PREFIX = "ScriptRunner-";
  private static final String LOG_FILE_NAME_SUFFIX = ".log";
  private static final DateFormat LOG_FILE_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd_HHmmss");
  public static final DateFormat LOG_FILE_LOG_TIMESTAMP_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
  
  private static File gLogFile = null;
  
  private static int gWarningCount = 0;
  
  private static boolean gLogDebug = false;  
  
  /**
   * Enables standard out logging.
   */
  public static void logToStandardOut(){
    if(!gLogToStandardOut){
      gLogWriterList.add(new OutputStreamWriter(System.out));
      gLogToStandardOut = true;
    }
  }
  
  /**
   * Turns debug logging on.
   */
  public static void enableDebugLogging(){
    gLogDebug = true;
  }
  
  /**
   * Adds a new log writer.
   * @param pWriter Writer to add.
   */
  public static void addLogWriter(Writer pWriter){
    gLogWriterList.add(pWriter);
  }
  
  /**
   * Removes a log writer from the list.
   * @param pWriter Writer to remove.
   */
  public static void removeLogWriter(Writer pWriter){
    gLogWriterList.remove(pWriter);
  }
  
  /**
   * Writes all output previously logged to the log file to a database CLOB.
   * @param pClob Clob to write to.
   */
  public static void writeLogToClob(Clob pClob){
    
    Writer lClobWriter;
    try {
      lClobWriter = pClob.setCharacterStream(0);
      IOUtils.copy(new FileInputStream(gLogFile), lClobWriter);
      lClobWriter.close();
    }
    catch (IOException e) {
      throw new ExInternal("Failed to copy output log to CLOB", e);
    }
    catch (SQLException e) {
      throw new ExInternal("Failed to initialise output CLOB", e);
    } 
  }
  
  /**
   * Creates a log file in the given directory. The log file is given a default name which includes the datestamp with a
   * resolution of seconds to provide uniqueness.
   * @param pLogDirectory Directory to create log file in.
   * @throws IOException If the log file cannot be created.
   */
  public static void initialiseLogFile(File pLogDirectory)
  throws IOException {
    if(!gLogFileInitialised){
      
      if(!pLogDirectory.isDirectory()){
        throw new IOException("Supplied log location " + pLogDirectory.getAbsolutePath() + " is not a directory");
      }
      
      String lLogFileName = LOG_FILE_NAME_PREFIX + LOG_FILE_DATE_FORMAT.format(new Date()) + LOG_FILE_NAME_SUFFIX;
      gLogFile = new File(pLogDirectory, lLogFileName);
      
      gLogWriterList.add(new FileWriter(gLogFile));
      gLogFileInitialised = true;
    }
  }
  
  /**
   * Internal method for logging a message to all loggers.
   * @param pString Message.
   */
  private static void log(String pString){
    for(Writer lWriter : gLogWriterList){
      String timeStamp = LOG_FILE_LOG_TIMESTAMP_FORMAT.format(new Date());
      try {
        lWriter.write("[" + timeStamp + "] ");

        lWriter.write(pString);     
        lWriter.write("\n");
        lWriter.flush();
      }     
      catch (IOException e) {
        throw new ExInternal("Logging exception", e);
      }
    }    
  }
  
  /**
   * Logs a debug message which will only be printed if debug logging is enabled.
   * @param pMessage Message to log.
   */
  public static void logDebug(String pMessage){
    if(gLogDebug){
      log(pMessage);
    }
  }
  
  /**
   * Logs the message to all loggers and prints to standard out, even if standard out logging is disabled.
   * @param pString Message to log.
   */
  public static void logAndEcho(String pString){
    log(pString);  
    if(!gLogToStandardOut){
      System.out.println(pString);
    }
  }
  
  /**
   * Gets the number of warnings which have occurred so far.
   * @return Warning count.
   */
  public static int getWarningCount(){
    return gWarningCount;
  }
  
  /**
   * Logs a message to all loggers using a PrintWriter format mask.
   * @param pFormatMask Format mask.
   * @param pArgs Arguments for format mask.
   */
  public static void logInfoFormatted(String pFormatMask, Object... pArgs){
    StringWriter lWriter = new StringWriter();
    PrintWriter lPrintWriter = new PrintWriter(lWriter);
    
    lPrintWriter.printf(pFormatMask, pArgs);
    
    logInfo(lWriter.toString());    
  }
  
  /**
   * Logs general information to all loggers.
   * @param pMessage Message to log.
   */
  public static void logInfo(String pMessage){
    log(pMessage);  
  }
  
  /**
   * Logs a warning message. If warnings are logged, the user is notified at the end of the run.
   * @param pMessage Warning message to log.
   */
  public static void logWarning(String pMessage){
    gWarningCount++;
    log("***WARNING***\n" + pMessage);
  }
  
  /**
   * Prints the stacktrace of an error to each logger.
   * @param pError Error to log.
   */
  public static void logError(Throwable pError){      
    //Loop through every logger to print stack trace information
    for(Writer lWriter : gLogWriterList){
      pError.printStackTrace(new PrintWriter(lWriter));
      try {
        lWriter.flush();
      }
      catch (IOException e) {
        throw new ExInternal("Logging exception", e);
      }
    }  
  }
  
  /**
   * Closes all log writers.
   */  
  public static void finaliseLogs(){    
    for(Writer lWriter : gLogWriterList){
      try {
        lWriter.close();
      }
      catch (IOException e) {
        throw new ExInternal("Logging exception", e);
      }
    }    
  }
  
}
