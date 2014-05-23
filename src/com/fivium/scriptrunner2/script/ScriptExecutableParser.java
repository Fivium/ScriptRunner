package com.fivium.scriptrunner2.script;


import com.fivium.scriptrunner2.Logger;
import com.fivium.scriptrunner2.ex.ExParser;
import com.fivium.scriptrunner2.script.parser.ParsedStatement;
import com.fivium.scriptrunner2.script.parser.ScriptParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A parser for creating a list of individually exectuable statements which can be run as part of a PatchScript or Loader.
 */
public class ScriptExecutableParser {
  
  private static final Pattern CONNECT_PATTERN = Pattern.compile("^[ \\t]*CONNECT[ \\t]+([A-Za-z0-9_]+)[ \\t]*;?$", Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
  private static final int CONNECT_PATTERN_SCHEMA_NAME_GROUP = 1;
  
  private static final Pattern DISCONNECT_PATTERN = Pattern.compile("^DISCONNECT[ \\t]*;?$", Pattern.CASE_INSENSITIVE);
  
  private static final Pattern COMMIT_PATTERN = Pattern.compile("^COMMIT[ \\t]*;?$", Pattern.CASE_INSENSITIVE);  
  
  private ScriptExecutableParser(){}  

  /**
   * Splits a string which may contain many statements into individually parsed statements using a {@link ScriptParser},
   * then converts those statements into a list of {@link ScriptExecutable}s. Note that legacy PatchScript syntax allows
   * a single statement to be composed of more than one executable part (i.e. a CONNECT followed by an anonymous block).
   * @param pScriptString Full script string to be parsed.
   * @param pAllowSQLBinds If true, statements will be parsed for bind variables.
   * @return List of ScriptExecutables from the given script string.
   * @throws ExParser If the input could not be parsed.
   */
  public static List<ScriptExecutable> parseScriptExecutables(String pScriptString, boolean pAllowSQLBinds) 
  throws ExParser {

    //Parse the script string into a list of parsed statements
    List<ParsedStatement> lParsedStatementList = ScriptParser.parse(pScriptString);
    
    List<ScriptExecutable> lResult = new ArrayList<ScriptExecutable>();
    
    //Map for tracking reoccurrences of the same SQL string within a script
    Map<String, Integer> lHashOccurrenceCounter = new HashMap<String, Integer>();
    
    List<ScriptSQL> lScriptSQLCounter = new ArrayList<ScriptSQL>();
    
    for(ParsedStatement lStatement : lParsedStatementList) {
      //Parse each nested script for CONNECT/DISCONNECT/SQL syntax and extract each one into an individual exectuable statement
      lResult.addAll(parseScriptExecutables(lStatement, pAllowSQLBinds, lHashOccurrenceCounter, lScriptSQLCounter));
    }
    
    return lResult;
  }
  
  /**
   * Internal method for splitting a single statement into executables.
   * @param pParsedStatement Statement to be parsed.
   * @param pAllowSQLBinds True if statment are to be parsed for binds.
   * @param pHashOccurrenceCounter Counter for tracking occurences of the same hash (indicating the same statement) within
   * a script.
   * @return List of parsed executables.
   */
  private static List<ScriptExecutable> parseScriptExecutables(ParsedStatement pParsedStatement, boolean pAllowSQLBinds, Map<String, Integer> pHashOccurrenceCounter, List<ScriptSQL> pScriptSQLCounter){
    
    //Get the complete string of the parsed statement for regex matching (trimmed)
    String lStatement = pParsedStatement.getStatementString().trim();
    List<ScriptExecutable> lResult = new ArrayList<ScriptExecutable>();    
    
    Matcher lConnectMatcher = CONNECT_PATTERN.matcher(lStatement);
    Matcher lDisconnectMatcher = DISCONNECT_PATTERN.matcher(lStatement);
    Matcher lCommitMatcher = COMMIT_PATTERN.matcher(lStatement);
        
    //Note use of find() for connect match - we only care about the first occurrence
    if(lConnectMatcher.find()){
      //Does the script start with a CONNECT statement?
      String lConnectSchemaName = lConnectMatcher.group(CONNECT_PATTERN_SCHEMA_NAME_GROUP);
      lResult.add(new ScriptConnect(lConnectSchemaName));
      Logger.logDebug("Parse: CONNECT " + lConnectSchemaName);
      
      //Legacy syntax allows CONNECT directly before a statement. If this has happened, add the remaining statement
      //as a seperate executable.
      String lRemainingSQL = lStatement.substring(lConnectMatcher.end()).trim();
      if(lRemainingSQL.length() > 0) {
        Logger.logDebug("Parse: SQL after CONNECT");
        //Replace the "CONNECT" out of the original parsed string
        pParsedStatement.replaceInUnescapedSegments(CONNECT_PATTERN, "");
        ScriptSQL lScriptSQL = new ScriptSQL(pParsedStatement, pAllowSQLBinds, pHashOccurrenceCounter, pScriptSQLCounter.size() + 1);
        lResult.add(lScriptSQL);
        pScriptSQLCounter.add(lScriptSQL);
        
        //Add a DISCONNECT after the executable so subsequent script execution can continue as the default user
        lResult.add(new ScriptDisconnect());
      }      
    }
    else if(lDisconnectMatcher.matches()){
      //DISCONNECT statement
      Logger.logDebug("Parse: DISCONNECT");
      lResult.add(new ScriptDisconnect());
    }
    else if(lCommitMatcher.matches()){
      //COMMIT statement
      Logger.logDebug("Parse: COMMIT");
      lResult.add(new ScriptCommit());
    }
    else {
      //Treat this as a standard SQL statement to be run as a PreparedStatement
      Logger.logDebug("Parse: SQL");
      ScriptSQL lScriptSQL = new ScriptSQL(pParsedStatement, pAllowSQLBinds, pHashOccurrenceCounter, pScriptSQLCounter.size() + 1);
      lResult.add(lScriptSQL);
      pScriptSQLCounter.add(lScriptSQL);
    }
    
    return lResult;
  }
}
