package com.fivium.scriptrunner2.loader;


import com.fivium.scriptrunner2.Logger;
import com.fivium.scriptrunner2.PromotionFile;
import com.fivium.scriptrunner2.ScriptRunner;
import com.fivium.scriptrunner2.ex.ExFatalError;
import com.fivium.scriptrunner2.ex.ExInternal;
import com.fivium.scriptrunner2.ex.ExLoader;
import com.fivium.scriptrunner2.ex.ExParser;
import com.fivium.scriptrunner2.ex.ExPromote;
import com.fivium.scriptrunner2.script.ScriptExecutable;
import com.fivium.scriptrunner2.script.ScriptExecutableParser;
import com.fivium.scriptrunner2.script.ScriptSQL;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;


/**
 * Loader for loading metadata into the database. <tt>MetadataLoader</tt>s consist of a sequence of one or more anonymous PL/SQL
 * blocks which contain named bind variables. The binds are populated and executed for each <tt>PromotionFile</tt> in the promote
 * which uses this loader.
 */
public class MetadataLoader 
extends SourceLoader {  
    
  protected final String mName;
  protected final String mLoaderFilePath;
  
  private List<ScriptExecutable> mExecutableList = null;
    
  private static final Pattern SUBSTITUTION_VARIABLE_PATTERN = Pattern.compile("\\$\\$\\{(.+?)\\}\\$\\$");
  
  private static final String SKIP_SUBSTITUTION_PROPERTY_NAME = "skip_substitution";
  private static final String AUTO_COMMIT_PROPERTY_NAME = "auto_commit"; //TODO enum - built in parameters
  
  private static final String LOADER_FILE_LOCATION = "ScriptRunner/Loaders/";
  private static final String LOADER_FILE_EXTENSION = ".sql";
  
  public static final String BIND_NAME_BLOB = "blob";
  public static final String BIND_NAME_CLOB = "clob";
  public static final String BIND_NAME_NAME = "name";
  
  public MetadataLoader(String pName){
    mName = pName;
    mLoaderFilePath = LOADER_FILE_LOCATION + pName + LOADER_FILE_EXTENSION;
  }
  
  protected MetadataLoader(String pName, String pLoaderPath){
    mName = pName;
    mLoaderFilePath = pLoaderPath;
  }
  
  /**
   * Replaces <tt>$${PARAMETER}$$</tt> references in the parsed statement string with their corresponding values
   * from the promotion file's property map.
   * @param pPromotionFile File about to be loaded.
   * @return Parsed statement string with substitution variables replaced with actual values.
   * @throws ExLoader If substitution variables cannot be resolved.
   */
  private static String replaceSubstitutionVariables(String pParsedStatementString, PromotionFile pPromotionFile) 
  throws ExLoader {
    
    //Replace variables only if one exists and a property has not been set instructing not to substitute 
    if(pParsedStatementString.indexOf("$${") > 0){
      if(!"true".equals(pPromotionFile.getPropertyMap().get(SKIP_SUBSTITUTION_PROPERTY_NAME))){
        Logger.logDebug("Performing variable substitution for " + pPromotionFile.getFilePath());
        
        StringBuffer lReplacedStatementString = new StringBuffer();
        
        Matcher lMatcher = SUBSTITUTION_VARIABLE_PATTERN.matcher(pParsedStatementString);
        
        while (lMatcher.find()) {
          String lParamName = lMatcher.group(1).toLowerCase();
          
          String lParamValue = pPromotionFile.getPropertyMap().get(lParamName);
          if(lParamValue == null){
            throw new ExLoader("Parameter value for '" + lParamName + "' substitution variable not defined");
          }
          
          lMatcher.appendReplacement(lReplacedStatementString, lParamValue);
        }
        lMatcher.appendTail(lReplacedStatementString);
        
        return lReplacedStatementString.toString();
      }
      else {
        Logger.logDebug("Skipping variable substitution for " + pPromotionFile.getFilePath() + " because " + SKIP_SUBSTITUTION_PROPERTY_NAME + " was true");
      }
    }
    
    //Skipping
    return pParsedStatementString;
  }  
  
  /**
   * Binds values from the given list into a PreparedStatement for a PromotionFile, handling special binds such as "blob"
   * (for the binary representation of the file) and "name" (for the file name). Remaining named binds are treated as
   * property values and the appropriate properties are bound in from the PromotionFile's property map. If streams are
   * opened for the purposes of reading a file's contents, they are added to pCloseableList.<br/><br/>
   * 
   * If a null PreparedStatement is provided, this method checks that all the bind names can be satisfied and throws
   * an error if they cannot. No binding is actually performed.
   * 
   * @param pStatement PreparedStatement to be bound into. Can be null.
   * @param pBindList List of named bind variables. The names should correspond to the bind's index.
   * @param pScriptRunner Current ScriptRunner for file resolving
   * @param pPromotionFile File to be bound.
   * @param pCloseableList A list which will be populated with 0 or more InputStreams or Readers in the case that any
   * are bound in to the statement.
   * @throws SQLException If the binding fails.
   * @throws ExLoader If a parameter cannot be resolved.
   * @throws ExFatalError If an unexpected error occurs.
   */
  private void bind(PreparedStatement pStatement, List<String> pBindList, ScriptRunner pScriptRunner, PromotionFile pPromotionFile, List<Closeable> pCloseableList) 
  throws SQLException, ExLoader, ExFatalError {

    if(pStatement != null){
      Logger.logDebug("Binding values for " + pPromotionFile.getFilePath());
    }
    else {
      Logger.logDebug("Verifying binds for " + pPromotionFile.getFilePath());
    }
      
    boolean lFileBound = false;    
    //Get a handle to the actual file
    File lFile;
    try {
      lFile = pScriptRunner.resolveFile(pPromotionFile.getFilePath());
    }
    catch (FileNotFoundException e) {
      //This is not expected at this point - manifest should have already been verified
      throw new ExFatalError("File not found", e);
    }
    
    //Bind in each bind variable
    for(int i=0; i<pBindList.size(); i++){
      String lBindName = pBindList.get(i);
      
      if(BIND_NAME_BLOB.equals(lBindName) && isFileBindingAllowed()){
        //Bind the file as a BLOB
        if(pStatement != null){          
          FileInputStream lInputStream;
          try {
            lInputStream = new FileInputStream(lFile);
            pCloseableList.add(lInputStream);
          }
          catch (FileNotFoundException e) {
            throw new ExFatalError("File not found", e);
          }
          pStatement.setBlob(i + 1, lInputStream);
        }
        lFileBound = true;
      }
      else if(BIND_NAME_CLOB.equals(lBindName) && isFileBindingAllowed()){
        //Bind the file as a CLOB
        if(pStatement != null){
        FileReader lReader;
          try {
            lReader = new FileReader(lFile);
            pCloseableList.add(lReader);
          }
          catch (FileNotFoundException e) {
            throw new ExFatalError("File not found", e);
          }
          pStatement.setClob(i + 1, lReader);
        }
        lFileBound = true;
      }
      else {
        //Bind a property value
        String lParamValue;
        if(BIND_NAME_NAME.equals(lBindName)){
          //Special case for the "name" property (filename)
          lParamValue = lFile.getName();
        }
        else {
          //Check property exists
          lParamValue = pPromotionFile.getPropertyMap().get(lBindName);
          if(lParamValue == null){
            throw new ExLoader("Parameter value for bind variable '" + lBindName + "' not defined");
          }
        }
        
        //Bind the value
        if(pStatement != null){
          pStatement.setString(i + 1, lParamValue);
        }
      }      
    }
    
    //If the file wasn't bound into the loader, log a warning (possibly a mistake)
    //TODO this should be tracked universally and checked against all executables in list
    if(!lFileBound && isFileBindingAllowed()){
      Logger.logInfo("Loader " + mName + " did not bind file into PL/SQL");
    }
    
  }
  
  /**
   * Indicates that this class is allowed to bind a promotion file (:clob, :blob binds) into the loader.
   * @return True.
   */
  protected boolean isFileBindingAllowed(){
    return true;
  }
  
  /**
   * Reads the PL/SQL statement(s) from the loader file into a string and parses them for bind variables so they are 
   * ready to be executed.
   * @param pScriptRunner ScriptRunner for file path resolving.
   * @throws ExFatalError If the statement cannot be read or compiled.
   */
  public void prepare(ScriptRunner pScriptRunner)
  throws ExFatalError {
    
    //Read the loader statement in
    String lFileContents;
    try {
      lFileContents = FileUtils.readFileToString(pScriptRunner.resolveFile(mLoaderFilePath));
    }
    catch (IOException e) {
      throw new ExFatalError("Failed to read contents of metadata loader " + mName, e);
    }

    try {
      mExecutableList = ScriptExecutableParser.parseScriptExecutables(lFileContents, true);
    }
    catch (ExParser e) {
      throw new ExFatalError("Failed to parse contents of metadata loader " + mName + ": " + e.getMessage(), e);
    }
  }
  
  private void closeCloseables(PromotionFile pPromotionFile, List<Closeable> pStreamsToClose){
    for(Closeable lCloseable : pStreamsToClose){
      try {
        lCloseable.close();
      }
      catch (IOException e) {
        Logger.logInfo("Failed to close input stream for file " + pPromotionFile.getFilePath() + ": " + e.getMessage());
      }
    }
  }
  
  /**
   * Validates that a promotion file will be able to be promoted by this loader. This involves checking that all required
   * bind variables and substitution variables are available in the file's property map.
   * @param pScriptRunner Current ScriptRunner.
   * @param pPromotionFile PromotionFile to be validated.
   * @throws ExPromote If validation fails.
   */
  public void validateForFile(ScriptRunner pScriptRunner, PromotionFile pPromotionFile)
  throws ExPromote {    
    List<Closeable> lStreamsToClose = new ArrayList<Closeable>();
    try {
      for(ScriptExecutable lExecutable : mExecutableList){
        if(lExecutable instanceof ScriptSQL){
          //If this fails an error will be thrown    
          String lStatementString = ((ScriptSQL) lExecutable).getParsedSQL();
          try {
            //Replace substitution variables in the statement
            lStatementString = replaceSubstitutionVariables(lStatementString, pPromotionFile);
            
            //"practice" a bind with a null prepared statement
            bind(null, ((ScriptSQL) lExecutable).getBindList(), pScriptRunner, pPromotionFile, null);
          }
          catch (ExLoader e) {
            throw new ExPromote("Validation of " + mName + " loader failed for file " + pPromotionFile.getFilePath() + ": " + e.getMessage(), e);
          }
          catch (SQLException e) {
            throw new ExPromote("Validation of prepare " + mName + " loader failed for file " + pPromotionFile.getFilePath() + ": " + e.getMessage(), e);
          }

        }
      }
    }
    finally {
      //Close all open FileInputStreams and Readers
      closeCloseables(pPromotionFile, lStreamsToClose);
    }
  }
  
  
  private PreparedStatement prepareStatement(ScriptSQL pScriptSQL, ScriptRunner pScriptRunner, PromotionFile pPromotionFile, Connection pConnection, List<Closeable> pStreamsToClose) 
  throws ExPromote {
    PreparedStatement lPreparedStatement;
    try {
      String lStatementString = pScriptSQL.getParsedSQL();
      //Replace substitution variables in the statement
      lStatementString = replaceSubstitutionVariables(lStatementString, pPromotionFile);
      
      //Get a prepared statement
      lPreparedStatement = pConnection.prepareStatement(lStatementString);
      
      //Perform the SQL binding
      bind(lPreparedStatement, pScriptSQL.getBindList(), pScriptRunner, pPromotionFile, pStreamsToClose);            
    }
    catch (SQLException e) {
      throw new ExPromote("Failed to prepare " + mName + " loader for file " + pPromotionFile.getFilePath() + ": " + e.getMessage(), e);
    }
    catch (ExLoader e) {
      throw new ExPromote("Failed to prepare " + mName + " loader for file " + pPromotionFile.getFilePath() + ": " + e.getMessage(), e);
    }
    return lPreparedStatement;
  }
  
  @Override
  public void doPromote(ScriptRunner pScriptRunner, PromotionFile pPromotionFile) 
  throws ExPromote {

    Logger.logInfo("\nPromote " + mName + " " + pPromotionFile.getFilePath());
    
    if(mExecutableList == null || mExecutableList.size() == 0){
      throw new ExInternal("No exectuables found for loader " + mName);
    }
        
    Connection lConn = pScriptRunner.getDatabaseConnection().getPromoteConnection();
    
    List<Closeable> lStreamsToClose = new ArrayList<Closeable>();
    try {
      for(ScriptExecutable lExecutable : mExecutableList){
        
        long lStart = System.currentTimeMillis();
        
        if(lExecutable instanceof ScriptSQL){          
          ScriptSQL lScriptSQL = (ScriptSQL) lExecutable;
          
          //Prepare the statement
          PreparedStatement lPreparedStatement = prepareStatement(lScriptSQL, pScriptRunner, pPromotionFile, lConn, lStreamsToClose);       
          
          //Run the statement
          try {      
            Logger.logInfo("Execute SQL as " + pScriptRunner.getDatabaseConnection().currentUserName() + " (" +  lScriptSQL.getStatementPreview() + "...)");
            lPreparedStatement.executeUpdate();
            lPreparedStatement.close();
          }
          catch (SQLException e) {
            throw new ExPromote("Failed to load file " + pPromotionFile.getFilePath() + ": " + e.getMessage(), e);
          }
          
        }        
        else {
          //For all other types, just execute the ScriptExecutable
          try {
            Logger.logInfo(lExecutable.getDisplayString());
            lExecutable.execute(pScriptRunner.getDatabaseConnection());
          }
          catch (SQLException e) {
            throw new ExPromote("Failed to load file " + pPromotionFile.getFilePath() + ": " + e.getMessage(), e);
          }
        }
        
        long lTime = System.currentTimeMillis() - lStart;
        Logger.logInfo("OK (took " + lTime + "ms)\n");
      }
    }
    finally {
      //Close all open FileInputStreams and Readers
      closeCloseables(pPromotionFile, lStreamsToClose);
    }
    
    //Check there's no outstanding uncommitted data - commit if allowed, or raise an error
    if(pScriptRunner.getDatabaseConnection().isTransactionActive()){
      if("true".equals(pPromotionFile.getPropertyMap().get(AUTO_COMMIT_PROPERTY_NAME))){
        Logger.logDebug("Committing as auto_commit property is true");
        try {      
          lConn.commit();
        }
        catch (SQLException e) {
          throw new ExPromote("Failed to commit file " + pPromotionFile.getFilePath() + ": " + e.getMessage(), e);
        }
      }
      else {
        //Rollback and throw an error
        try {      
          lConn.rollback();
        }
        catch (SQLException e) {
          //Any errors here should not take precedence over the "main" error
          Logger.logWarning("Failed to rollback during error handler - promote may be in inconsistent state");
        }
        
        throw new ExPromote("Uncommitted data detected after promoting " + pPromotionFile.getFilePath() + 
                            "\nFix loader statement or consider using " + AUTO_COMMIT_PROPERTY_NAME + " property");
      }
    }
    
    //Disconnect from a proxy connection if required (reset the connection to its original state)
    if(pScriptRunner.getDatabaseConnection().isProxyConnectionActive()){
      try {
        pScriptRunner.getDatabaseConnection().disconnectProxyUser();
      }
      catch (SQLException e) {
        Logger.logWarning("Failed to disconnect proxy user after promoting file " + pPromotionFile.getFilePath() + ": " + e.getMessage());
        Logger.logError(e);
      }
    }
    
  }
  
  public String getName() {
    return mName;
  }

  public String getLoaderFilePath() {
    return mLoaderFilePath;
  }

}
