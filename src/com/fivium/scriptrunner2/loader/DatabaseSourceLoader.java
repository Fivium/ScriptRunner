package com.fivium.scriptrunner2.loader;


import com.fivium.scriptrunner2.Logger;
import com.fivium.scriptrunner2.PromotionFile;
import com.fivium.scriptrunner2.ScriptRunner;
import com.fivium.scriptrunner2.ex.ExFatalError;
import com.fivium.scriptrunner2.ex.ExParser;
import com.fivium.scriptrunner2.ex.ExPromote;
import com.fivium.scriptrunner2.script.ScriptExecutable;
import com.fivium.scriptrunner2.script.ScriptExecutableParser;
import com.fivium.scriptrunner2.script.ScriptSQL;

import java.io.IOException;

import java.util.List;

import org.apache.commons.io.FileUtils;


/**
 * A loader for loading Database Source.  Database Source typically includes packages, triggers, views, and etc. A
 * DatabaseSource promotion file must consist of single SQL DDL statement to be loaded by this loader.
 */
public class DatabaseSourceLoader 
extends SourceLoader {
    
  @Override
  public void doPromote(ScriptRunner pScriptRunner, PromotionFile pFile)
  throws ExPromote {
    
    long lStart = System.currentTimeMillis();
    Logger.logInfo("\nPromote DatabaseSource " + pFile.getFilePath());
    //Read the DBSource file in
    String lFileContents;
    try {
      lFileContents = FileUtils.readFileToString(pScriptRunner.resolveFile(pFile.getFilePath()));
    }
    catch (IOException e) {
      throw new ExFatalError("Failed to read contents of file " + pFile.getFilePath(), e);
    }

    List<ScriptExecutable> lExecutableList;
    try {
      lExecutableList = ScriptExecutableParser.parseScriptExecutables(lFileContents, false);
    }
    catch (ExParser e) {
      throw new ExFatalError("Failed to read contents of file " + pFile.getFilePath() + ": " + e.getMessage(), e);
    }
    
    Logger.logDebug("Validating source file");
    
    //Validate contents
    for(ScriptExecutable lExecutable : lExecutableList){
      if(!(lExecutable instanceof ScriptSQL)){
        throw new ExPromote("File " + pFile.getFilePath() + " has invalid contents: non-SQL markup not permitted in Database Source, " +
          "but a " + lExecutable.getDisplayString() + " command was found");
      }
    }
    
    try {
      for(ScriptExecutable lExecutable : lExecutableList){
        lExecutable.execute(pScriptRunner.getDatabaseConnection());
      }
    }
    catch (Throwable th){      
      throw new ExPromote("Failed to promote DatabaseSource " + pFile.getFilePath() + ": " + th.getMessage(), th);
    }
    long lTime = System.currentTimeMillis() - lStart;
    Logger.logInfo("OK (took " + lTime + "ms)");
    
  }

}
