package com.fivium.scriptrunner2;


import com.fivium.scriptrunner2.builder.ManifestBuilder;
import com.fivium.scriptrunner2.ex.ExManifest;
import com.fivium.scriptrunner2.ex.ExParser;
import com.fivium.scriptrunner2.loader.BuiltInLoader;
import com.fivium.scriptrunner2.loader.MetadataLoader;
import com.fivium.scriptrunner2.util.ScriptRunnerVersion;
import com.fivium.scriptrunner2.util.XFUtil;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * Parser for parsing a manifest file which will be used to control a promotion. This subclass requires that all entries
 * are specified in order, files are only specified once (unless they are override entries) and a promotion properties map
 * must be specified.
 */
public class PromotionManifestParser 
extends ManifestParser {
  
  public PromotionManifestParser(File pFile) {
    super(pFile);
  }
  
  public List<PromotionFile> getPromotionFileList(){
    //Downcast to a list and implicitly upcast back to a PromotionFile list
    return (List) mManifestEntryList;
  }
  
  /**
   * Verifies the contents of this manifest file by resolving all its file references, checking that referenced loaders
   * exist, and optionally checking file hashes if required.
   * @param pScriptRunner ScriptRunner to use as the base directory for file checking.
   * @throws ExManifest If any manifest entry is invalid.
   */
  public void verifyManifest(ScriptRunner pScriptRunner) 
  throws ExManifest {
    
    //Verify the ScriptRunner versions are compatible (unless this behaviour is explicitly overriden)
    if(!pScriptRunner.hasCommandLineOption(CommandLineOption.SKIP_VERSION_CHECK)){
      String lCurrVersion = ScriptRunnerVersion.getVersionNumber();
      String lManifestVersion =  mPromotionPropertyMap.get(SCRIPTRUNNER_VERSION_PROPERTY);
      if(!lCurrVersion.equals(lManifestVersion)){
        throw new ExManifest("Manifest version incompatible. Version " + lCurrVersion + " cannot safely execute a manifest built by version " + lManifestVersion);
      }
    }
    
    //Verify all loaders exist
    for(MetadataLoader lLoader : mLoaderMap.values()){
      try {
        pScriptRunner.resolveFile(lLoader.getLoaderFilePath());
      }
      catch (FileNotFoundException e) {
        throw new ExManifest("Loader file for loader " + lLoader.getName() + " cannot be located", e);
      }
    }
    
    Set<String> lAllFilePaths = pScriptRunner.allFilePathsInBaseDirectory();
    
    //Verify all files exist and they reference valid loaders
    for(PromotionFile lPromotionFile : getPromotionFileList()){      
      //Check file existence
      File lFile;
      try {
        lFile = pScriptRunner.resolveFile(lPromotionFile.getFilePath());
      }
      catch (FileNotFoundException e) {
        throw new ExManifest("Promotion file " + lPromotionFile.getFilePath() + " cannot be located", e);
      }
      
      lAllFilePaths.remove(pScriptRunner.relativeFilePath(lFile));
      
      String lFileHash;
      try {
        lFileHash = lPromotionFile.generateFileHash(pScriptRunner);
      }
      catch (IOException e) {
        throw new ExManifest("Could not generate hash for file " + lPromotionFile.getFilePath(), e);
      }
            
      //Do a hash code check if required
      if(!pScriptRunner.hasCommandLineOption(CommandLineOption.SKIP_HASH_CHECK)){        
        String lPropertyHash = lPromotionFile.getPropertyMap().get(ManifestBuilder.PROPERTY_NAME_FILE_HASH);
        if(XFUtil.isNull(lPropertyHash)){
          throw new ExManifest("Cannot perform hash check for " + lPromotionFile.getFilePath() + " as the " + ManifestBuilder.PROPERTY_NAME_FILE_HASH + " property is not specified");
        }
        
        if(!lPropertyHash.equals(lFileHash)){
          throw new ExManifest("Hash verification failed for file " + lPromotionFile.getFilePath() + " - expected " + lPropertyHash + " but got " + lFileHash);
        }
        Logger.logDebug("Hash check OK for file " + lPromotionFile.getFilePath());
      }
    }
    
    //If there are files in the archive that are not listed in the manifest we should issue a warning
    if(lAllFilePaths.size() > 0){
      Logger.logWarning(lAllFilePaths.size() + " file(s) found in base directory but not listed in the manifest file:");
      for(String lPath : lAllFilePaths){
        Logger.logInfo(lPath);
      }
    }
    
  }
  
  @Override
  public void parse()
  throws FileNotFoundException, IOException, ExParser, ExManifest {
    //Call the main parse method
    super.parse();
    
    //Verify that we found a promotion properties line
    if(mPromotionPropertyMap == null){
      throw new ExParser("Manifest missing mandatory PROMOTION property definitions");
    }
  }  
  
  @Override
  protected ManifestEntry parseManifestEntryLine(String pLine, int lHighestSequencePosition)
  throws ExParser, ExManifest {
    ManifestEntry lManifestEntry = ManifestEntry.parseManifestFileLine(pLine, true);    
      
    //Validate that sequence values are properly sequential
    if(lManifestEntry.getSequencePosition() <= lHighestSequencePosition){
      throw new ExManifest("Manifest entry for file " + lManifestEntry.getSequencePosition() + ": " + lManifestEntry.getFilePath() + 
                         " is not in a valid order - an entry for position " + lHighestSequencePosition + " has already been processed."); 
    }
    
    //Validate that file was not already implicated (note: utility scripts are allowed to be implicated multiple times)
    if(mFinalProcessedFilePathSet.contains(lManifestEntry.getFilePath()) 
       && !lManifestEntry.isForcedDuplicate() 
       && !BuiltInLoader.LOADER_NAME_SCRIPTRUNNER_UTIL.equals(lManifestEntry.getLoaderName())
    ) {
      throw new ExManifest("Manifest entry for file " + lManifestEntry.getFilePath() + " already implicated and not marked as an explicit duplicate");
    }
    
    return lManifestEntry;    
  }

  protected Map<String, String> parsePromotionPropertiesLine(String pLine) 
  throws ExParser {
    
    //Call superclass method to attempt to parse properties
    Map<String, String> lProperties = super.parsePromotionPropertiesLine(pLine);
    
    if(lProperties != null){
      //Additional validation - Check for mandatory properties
      String lProp = lProperties.get(PROMOTION_LABEL_PROPERTY);
      if(XFUtil.isNull(lProp)){
        throw new ExParser("Promotion properties: " + PROMOTION_LABEL_PROPERTY + " must be specified");
      }
            
      lProp = lProperties.get(SCRIPTRUNNER_VERSION_PROPERTY);
      if(XFUtil.isNull(lProp)){
        throw new ExParser("Promotion properties: " + SCRIPTRUNNER_VERSION_PROPERTY + " must be specified");
      }
      Logger.logInfo("Manifest ScriptRunner version: " + lProp);
    }
      
    return lProperties;
  }
}
