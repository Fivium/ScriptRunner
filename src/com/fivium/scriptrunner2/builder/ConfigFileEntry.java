package com.fivium.scriptrunner2.builder;


import com.esotericsoftware.wildcard.Paths;

import com.fivium.scriptrunner2.Logger;
import com.fivium.scriptrunner2.ManifestParser;
import com.fivium.scriptrunner2.ex.ExParser;
import com.fivium.scriptrunner2.loader.BuiltInLoader;

import java.io.File;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;


/**
 * Encapsulation of a parsed set of lines from a manifest builder configuration file. A ConfigFileEntry must contain
 * a "Files:" instruction providing a wildcard path of files to select, and "Loader:", "StartOffset:" and "FileOffset:"
 * instructions to determine the default loader name for the files and how they will be ordered in the manifest.<br/><br/>
 *
 * This object is also responsible for generating {@link BuilderManifestEntry}s for each file its wildcard hits, although
 * it does not determine their final sequence positions (this is done by the {@link ManifestBuilder}).
 */
public class ConfigFileEntry {
  
  private final String mWildcardFilePath;
  private final String mLoaderName;
  private final int mStartOffset;
  private final int mFileOffset;
  
  private final Map<String, String> mPropertyMap = new HashMap<String, String>();
  
  private final ManifestBuilder mManifestBuilder;
  private final TreeMap<String, BuilderManifestEntry> mManifestEntryMap = new TreeMap<String, BuilderManifestEntry>();
  
  private static final Set<String> DISALLOWED_PROPERTY_NAMES = new HashSet<String>();
  static {
    DISALLOWED_PROPERTY_NAMES.add(ManifestBuilder.PROPERTY_NAME_FILE_VERSION);
    DISALLOWED_PROPERTY_NAMES.add(ManifestBuilder.PROPERTY_NAME_FILE_HASH);
  }
  
  /**
   * Constructs a new ConfigFileEntry.
   * @param pManifestBuilder ManifestBuilder which this ConfigFileEntry is associated with.
   * @param pWildcardFilePath The glob wildcard path of files to be selected by this entry.
   * @param pLoaderName The name of the default loader for ManifestEntries created by this ConfigFileEntry.
   * @param pStartOffset The sequence offset from the previous set of ManifestEntries for entries created by this ConfigFileEntry.
   * @param pFileOffset The sequence offset between the individual ManifestEntries of this ConfigFileEntry.
   * @param pPropertiesString String representation of the default property map for each  ManifestEntries of this ConfigFileEntry.
   * @throws ExParser If the propertiy map string is invalid.
   */
  public ConfigFileEntry(ManifestBuilder pManifestBuilder, String pWildcardFilePath, String pLoaderName, int pStartOffset, int pFileOffset, String pPropertiesString) 
  throws ExParser {
    
    mManifestBuilder = pManifestBuilder;
    mWildcardFilePath = pWildcardFilePath;
    mLoaderName = pLoaderName;
    mStartOffset = pStartOffset;
    mFileOffset = pFileOffset;

    Map<String, String> lPropertyMap = ManifestParser.parsePropertyMapString(pPropertiesString);
    //Check no reserved property names have been used
    for(String lDisallowedProperty : DISALLOWED_PROPERTY_NAMES){
      if(lPropertyMap.keySet().contains(lDisallowedProperty)){
        throw new ExParser("Config properties may not specify reserved property " + lDisallowedProperty);
      }
    }
    
    mPropertyMap.putAll(lPropertyMap);
  }
  
  /**
   * Uses the glob wildcard of this ConfigFileEntry to select 0 or more files, and constructs a ManifestEntry for each 
   * matched file, if it has not already been matched by the current ManifestBuilder. The created ManifestEntries are
   * stored in a list in this object.
   */
  public void generateManifestEntries(){
    
    Paths lPaths = new Paths();
    String lBasePath = mManifestBuilder.getBaseDirectory().getPath();
    lPaths.glob(lBasePath, mWildcardFilePath);
    
    for(File lFile : lPaths.getFiles()){
      
      String lFilePath = mManifestBuilder.relativeFilePath(lFile);
      
      if(lFile.isDirectory()){
        Logger.logDebug("Skip " + lFilePath + "; directory");
      }
      else if (!lFile.exists()){
        Logger.logDebug("Skip " + lFilePath + "; does not exist");
      }
      else {
        mManifestBuilder.registerPathToConfigEntryMapping(lFilePath, this);
        
        if(mManifestBuilder.checkNotAlreadyGlobbed(lFilePath)){
          //Get the name of the loader - either from an augmentation entry or this config file entry
          String lLoaderName = mManifestBuilder.manifestEntryLoaderName(lFilePath, this);
          
          //Special case for ScriptRunnerUtil loader - these files can be added multiple to the manifest multiple times
          if(!BuiltInLoader.LOADER_NAME_SCRIPTRUNNER_UTIL.equals(lLoaderName)){
            //Register that this file has been processed so it isn't processed again            
            mManifestBuilder.addGlobbedFile(lFilePath);
          }
          
          //Generate the default property map for this file
          Map<String, String> lPropertyMap = mManifestBuilder.generateManifestEntryPropertyMap(lFilePath, this, null);        
  
          mManifestEntryMap.put(lFilePath, new BuilderManifestEntry(false, lFilePath, lLoaderName, lPropertyMap, false));
        }
        else {
          Logger.logDebug("Skip " + lFilePath + "; already added");
        }
      }
      
    }    
  }
  
  /**
   * Gets the list of ManifestEntries created by invoking {@link #generateManifestEntries}.
   * @return List of ManifestEntries.
   */
  public List<BuilderManifestEntry> getEntryList(){
    return new ArrayList<BuilderManifestEntry>(mManifestEntryMap.values());
  }

  /**
   * Gets the default loader name for files matched by this entry.
   * @return Default loader name.
   */
  String getLoaderName() {
    return mLoaderName;
  }
  
  /**
   * Gets the default property map for files matched by this entry.
   * @return Default property map.
   */
  Map<String, String> getPropertyMap(){
    return mPropertyMap;
  }

  /**
   * Gets the start offset, relative to files selected by the previous ConfigFileEntry, for files in this entry.
   * @return Start numbering offset.
   */
  public int getStartOffset() {
    return mStartOffset;
  }

  /**
   * Gets the numbering offset used when numbering the files in this entry.
   * @return File numbering offset.
   */
  public int getFileOffset() {
    return mFileOffset;
  }

  /**
   * Gets the wildcard string used for selecting files for this entry.
   * @return Wildcard path string.
   */
  public String getWildcardFilePath() {
    return mWildcardFilePath;
  }
}
