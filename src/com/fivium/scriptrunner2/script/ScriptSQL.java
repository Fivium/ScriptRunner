package com.fivium.scriptrunner2.script;


import com.fivium.scriptrunner2.Logger;
import com.fivium.scriptrunner2.database.DatabaseConnection;
import com.fivium.scriptrunner2.ex.ExInternal;
import com.fivium.scriptrunner2.script.parser.ParsedStatement;
import com.fivium.scriptrunner2.util.HashUtil;

import java.sql.SQLException;
import java.sql.Statement;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;


public class ScriptSQL 
implements ScriptExecutable {  
  
  public static final String BIND_REPLACE_STRING = " ? "; //Oracle Bind string
  
  //Bind variable names must start with an alphanumeric and contain only subsequently alphanumerics, $, _ or # symbols
  private static final Pattern BIND_VARIABLE_PATTERN = Pattern.compile(":[A-Za-z0-9][\\w$#]*");
  
  
  private final String mOriginalSQL;
  private String mParsedSQL;
  private final List<String> mBindList = new ArrayList<String>();
  private final String mExternalHash;
  private final boolean mIsComment;
  /** Index of this SQL within its containing PatchScript */
  private final int mScriptIndex;
  
  public ScriptSQL(ParsedStatement pParsedStatement, boolean pContainsBinds, Map<String, Integer> pHashOccurrenceCounter, int pScriptIndex){
    mOriginalSQL = pParsedStatement.getStatementString();   
    if(pContainsBinds){
      //Parse binds if required
      parseBinds(pParsedStatement);
    }
    else {
      mParsedSQL = pParsedStatement.getStatementString();
    }
    
    mIsComment = pParsedStatement.isAllCommentsOrEmpty();
    
    //Establish the unique suffix to append to the hash for this SQL statement - increment whatever is in the counter
    //map by 1 and set the new value in the map.
    String lInternalHash = HashUtil.hashString(mParsedSQL); //generateInternalHash();
    int lHashOccurrenceCount = 0;
    if(pHashOccurrenceCounter.containsKey(lInternalHash)){
      lHashOccurrenceCount = pHashOccurrenceCounter.get(lInternalHash);
    }
    mExternalHash = lInternalHash + "-" + ++lHashOccurrenceCount;
    pHashOccurrenceCounter.put(lInternalHash, lHashOccurrenceCount);
    
    mScriptIndex = pScriptIndex;
  }
  
  /**
   * Parses the statement to replace named bind syntax (":bind") with standard JDBC bind syntax ("?") and populates
   * this object's list of bind variable names in the order they were encountered in the statement.<br><br>
   * 
   * Note that whilst Oracle's JDBC driver provides the mechanism to set binds by name (using an OraclePreparedStatement),
   * it does not provide a mechanism to retrieve the names of the binds from the statement, so a manual parse will
   * always be required.
   */
   private void parseBinds(ParsedStatement pParsedStatement){
     Logger.logDebug("Parsing statement (SQL below) for binds");
     
     //Replace ":bind" syntax with "?" bind syntax and get a list of the names of the binds which were replaced
     List<String> lBindList = pParsedStatement.replaceInUnescapedSegments(BIND_VARIABLE_PATTERN, BIND_REPLACE_STRING);
     
     for(String lBindName : lBindList){
       //Chop off the preceding ":" character and record the bind name
       mBindList.add(lBindName.substring(1).toLowerCase());
       Logger.logDebug("Found bind " + lBindName);
     }
     
     //The parsed statement is now rewritten, so serialise it as this object's parsed statement
     mParsedSQL = pParsedStatement.getStatementString();
     
     Logger.logDebug("Parsed result:");
     Logger.logDebug(mParsedSQL);
   }
  
  /**
   * Runs the statement on the database ONLY if it is not a comment and does not have any binds. Statements with binds
   * must be executed manually.
   * @param pDatabaseConnection Connection to use.
   * @throws SQLException If the statement fails.
   */
  public void execute(DatabaseConnection pDatabaseConnection) 
  throws SQLException {
    
    if(!mIsComment) {
      if(mBindList.size() > 0){
        throw new ExInternal("Cannot execute bindable statement in this way");
      }      
      //Don't use a PreparedStatement as binds will never be parsed
      Statement lStatement = pDatabaseConnection.getPromoteConnection().createStatement();
      lStatement.execute(mParsedSQL);

      lStatement.close();
    }
    else {
      Logger.logDebug("Skipping execution of comment block");
    }
      
  }

  @Override
  public String getDisplayString() {    
    return "[SQL hash " + mExternalHash + "]\n" + (mIsComment ? "[Comment block; will not be executed]\n" : "") + mParsedSQL.trim();
  }
  
  /**
   * Gets a preview of the first 50 characters of this SQL statement. Linebreaks are converted to spaces and spaces are
   * collapsed so as much meaningful content is displayed as possible.
   * @return A simplified statement preview.
   */
  public String getStatementPreview(){
    String lStatementPreview = mParsedSQL.replaceAll("[\\r\\n\\t]", " ").replaceAll(" +", " ").trim();
    lStatementPreview = lStatementPreview.substring(0, Math.min(lStatementPreview.length(), 50));     
    return lStatementPreview;
  }
  
  /**
   * Gets the parsed SQL for this statment, with bind variables replaced.
   * @return Parsed SQL.
   */
  public String getParsedSQL() {
    return mParsedSQL;
  }
  
  /**
   * Gets the list of bind variable names found in this SQL statement.
   * @return List of bind names.
   */
  public List<String> getBindList(){
    return mBindList;
  }
  
  /**
   * Gets the unique hash of this SQL statement within its parent script. This differentiates between different instances
   * of the same statement by appending an index to the statement hash, so is guaranteed to be a unique identified within
   * the context of the parent script.
   * @return The MD5 hash of this SQL, with a unique suffix.
   */
  public String getHash(){    
    return mExternalHash;
  }

  /**
   * Gets the index of this SQL statement within its parent PatchScript.
   * @return Statement index.
   */
  public int getScriptIndex() {
    return mScriptIndex;
  }
}
