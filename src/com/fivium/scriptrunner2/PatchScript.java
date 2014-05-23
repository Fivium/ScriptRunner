package com.fivium.scriptrunner2;


import com.fivium.scriptrunner2.ex.ExParser;
import com.fivium.scriptrunner2.script.ScriptExecutable;
import com.fivium.scriptrunner2.script.ScriptExecutableParser;

import java.io.File;
import java.io.IOException;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;


/**
 * A PatchScript represents a parsed patch script file, which is used to promote DDL and DML changes to the database.
 * They may also contain instructions to ScriptRunner to control how to connect to the database and when to commit or
 * rollback transactions.<br/><br/>
 *
 * PatchScripts are composed of one or more {@link ScriptExecutable}s which should be executed in order. A PatchScript
 * is uniquely identified by a faceted filename, which is parsed when this object is created.
 */
public class PatchScript {
  
  private final String mPatchLabel;
  private final int mPatchNumber;
  private final String mDescription;
  private final List<ScriptExecutable> mExecutableList;
  
  private final String mPatchFileHash;
  private final int mPromotionSequencePosition;  
  private final String mOriginalPatchString;
  private final String mFileVersion;
  
  private static final Pattern FILENAME_PATTERN = Pattern.compile("^([A-Z]{5,})([0-9]{4,}) *\\((.+)\\) *\\.sql$"); //, Pattern.CASE_INSENSITIVE removed
  private static final int FILENAME_PATTERN_TYPE_GROUP = 1;
  private static final int FILENAME_PATTERN_NUMBER_GROUP = 2;
  private static final int FILENAME_PATTERN_DESCRIPTION_GROUP = 3;  
  
  private static final String PRINT_STATEMENT_DIVIDER = "\n========================================\n";
  
  /**
   * Prints the parsed contents of each PatchScript in the given path list to standard out. Each statement in the PatchScript
   * is seperated by an obvious string delimiter. Any errors encountered during the parse are also printed to standard out
   * and are not re-thrown. This output is for debugging purposes only and should not be processed programatically.
   * @param pBaseDirectory Base directory for relative path evalulation.
   * @param pScriptPathList List of paths to all required PathScripts.
   * @return True if parsing was successful, false otherwise.
   */
  public static boolean printScriptsToStandardOut(File pBaseDirectory, List<String> pScriptPathList){
    
    boolean lSuccess = true;
    
    for(String lPath : pScriptPathList){
      
      String lFileContents;
      try {
        File lPatchFile = new File(lPath);
        if(!lPatchFile.isAbsolute()){
          //If not absolute, evaluate from the base directory
          lPatchFile = new File(pBaseDirectory, lPath);
        }
        
        System.out.println("\n********** " + lPatchFile.getName() + " **********\n");
        
        lFileContents = FileUtils.readFileToString(lPatchFile);
        PatchScript lPatchScript = createFromString(lPatchFile.getName(), lFileContents);
        
        System.out.println("Patch label: " +  lPatchScript.getPatchLabel());
        System.out.println("Patch number: " +  lPatchScript.getPatchNumber());
        System.out.println("Patch description: " +  lPatchScript.getDescription());
        
        for(ScriptExecutable lExec : lPatchScript.getExecutableList()){
          System.out.println(PRINT_STATEMENT_DIVIDER);
          System.out.println(lExec.getDisplayString());
        }
        System.out.println(PRINT_STATEMENT_DIVIDER);
        
      }
      catch (IOException e) {
        System.out.println("ERROR: Could not read PatchScript file");
        System.out.println("Reason (see log for details): " + e.getMessage());
        Logger.logError(e);
        lSuccess  = false;
      }
      catch (ExParser e) {
        System.out.println("ERROR: PATCHSCRIPT COULD NOT BE PARSED");
        System.out.println("Reason (see log for details): " + e.getMessage());
        Logger.logError(e);
        lSuccess = false;
      }
    }
    
    return lSuccess;    
  }
  
  /**
   * Constructs a new PatchScript by parsing the given file contents.
   * @param pFileName File name of the PatchScript.
   * @param pPatchContents File contents.
   * @return The new PatchScript.
   * @throws ExParser If the file contents or file name cannot be parsed.
   */
  public static PatchScript createFromString(String pFileName, String pPatchContents) 
  throws ExParser {
    return createFromString(pFileName, pPatchContents, "unavailable", "unavailable");
  }

  /**
   * Constructs a new PatchScript by parsing the given file contents.
   * @param pFileName File name of the PatchScript.
   * @param pPatchContents File contents.
   * @param pFileHash File hash of the patch script.
   * @param pFileVersion Version of the patch script.
   * @return The new PatchScript.
   * @throws ExParser If the file contents or file name cannot be parsed.
   */
  public static PatchScript createFromString(String pFileName, String pPatchContents, String pFileHash, String pFileVersion) 
  throws ExParser {    
    return new PatchScript(pFileName, pPatchContents, pFileHash, 0, pFileVersion);
  }
  
  /**
   * Constructs a new PatchScript by reading the contents of a PromotionFile.
   * @param pResolver Resolver for finding the file.
   * @param pPromotionFile File to be parsed.
   * @return The new PatchScript.
   * @throws IOException If the file cannot be read.
   * @throws ExParser If the file contents or file name cannot be parsed.
   */
  public static PatchScript createFromPromotionFile(FileResolver pResolver, PromotionFile pPromotionFile) 
  throws IOException, ExParser {
    File lFile = pResolver.resolveFile(pPromotionFile.getFilePath());
    String lFileContents = FileUtils.readFileToString(lFile);
    return new PatchScript(lFile.getName(), lFileContents, pPromotionFile.getFileHash(), pPromotionFile.getSequencePosition(), pPromotionFile.getFileVersion());
  }
  
  /**
   * Constructs a new PatchScript.
   * @param pFileName Patch file name.
   * @param pFileContents Contents of the file.
   * @param pPatchFileHash Hash of the file.
   * @param pPromotionSequencePosition Position within the overall promotion.
   * @param pFileVersion VCS version of the file.
   * @throws ExParser If the contents or filename cannot be parsed.
   */
  private PatchScript(String pFileName, String pFileContents, String pPatchFileHash, int pPromotionSequencePosition, String pFileVersion) 
  throws ExParser {
    
    //Use regex to split the filename into its component parts
    Matcher lMatcher = FILENAME_PATTERN.matcher(pFileName);
    if(lMatcher.matches()){
      mPatchLabel = lMatcher.group(FILENAME_PATTERN_TYPE_GROUP);
      mPatchNumber = Integer.parseInt(lMatcher.group(FILENAME_PATTERN_NUMBER_GROUP));
      mDescription = lMatcher.group(FILENAME_PATTERN_DESCRIPTION_GROUP);

      Logger.logDebug("Parsed patch filename " + pFileName + ": Patch Label = " + mPatchLabel + " Number = "+ mPatchNumber + " Description = " + mDescription);
    }
    else {
      throw new ExParser("Invalid patch filename '" + pFileName + "'. Expected format is 'PATCHLABEL##### (description).sql'");
    }
    
    //Split the nested scripts into individual executable scripts
    mExecutableList = ScriptExecutableParser.parseScriptExecutables(pFileContents, false);
    
    mPatchFileHash = pPatchFileHash;
    mPromotionSequencePosition = pPromotionSequencePosition;
    mOriginalPatchString = pFileContents;
    mFileVersion = pFileVersion;
  }
  
  /**
   * Gets the original contents of the file used to create this PatchScript, before it was parsed.
   * @return Original file contents.
   */
  public String getOriginalPatchString(){
    return mOriginalPatchString;
  }
  
  /**
   * Gets this PatchScript's list of ScriptExecutables.
   * @return Executable list.
   */
  public List<ScriptExecutable> getExecutableList(){
    return mExecutableList;
  }
  
  /**
   * Gets the unique display name of this PatchScript. This is the label concatenated with the number.
   * @return Display name.
   */
  public String getDisplayName(){
    return mPatchLabel + " " + mPatchNumber;
  }

  /**
   * Gets the patch label, e.g. PATCHCORE, POSTPATCHCORE, etc.
   * @return Patch label.
   */
  public String getPatchLabel() {
    return mPatchLabel;
  }

  /**
   * Gets the number sequence of this PatchScript.
   * @return Patch number.
   */
  public int getPatchNumber() {
    return mPatchNumber;
  }
  
  /**
   * Gets the position of this PatchScript within its overall promotion label.
   * @return Promotion position.
   */
  public int getPromotionSequencePosition(){
    return mPromotionSequencePosition;
  }
  
  /**
   * Gets the file hash of this patch's original file.
   * @return File hash.
   */
  public String getPatchFileHash(){
    return mPatchFileHash;
  }

  /**
   * Gets the description of this PatchScript as specified in the parenthesised part of the file name.
   * @return Patch description.
   */
  public String getDescription() {
    return mDescription;
  }
  
  /**
   * Gets the VCS version string for the file which created this PatchScript.
   * @return Version number.
   */
  public String getFileVersion() {
    return mFileVersion;
  }
}
