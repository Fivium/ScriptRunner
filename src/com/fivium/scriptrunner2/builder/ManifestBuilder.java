package com.fivium.scriptrunner2.builder;


import com.fivium.scriptrunner2.FileResolver;
import com.fivium.scriptrunner2.Logger;
import com.fivium.scriptrunner2.ManifestEntry;
import com.fivium.scriptrunner2.ManifestParser;
import com.fivium.scriptrunner2.ScriptRunner;
import com.fivium.scriptrunner2.ex.ExFatalError;
import com.fivium.scriptrunner2.ex.ExInternal;
import com.fivium.scriptrunner2.ex.ExManifestBuilder;
import com.fivium.scriptrunner2.ex.ExParser;
import com.fivium.scriptrunner2.util.ScriptRunnerVersion;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.text.SimpleDateFormat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.NameFileFilter;
import org.apache.commons.io.filefilter.RegexFileFilter;


/**
 *  Class for controlling the building of a manifest file. A manifest file is an ordered list of {@link ManifestEntry}s
 *  which is then in turn serialised by a {@link ManifestSerialiser}.<br/><br/>
 *
 *  The process for building a manifest is outlined below.
 *  <ol>
 *  <li>Locate and parse a configuration file into {@link ConfigFileEntry}s. A ConfigFileEntry selects a set of files
 *  using a glob wildcard and provides the builder with instructions on how to number the files (i.e. the numbering offsets).<br/><br/>
 *
 *  The ConfigFileEntry may also provide a default set of properties for each file it selects and will provide a loader name
 *  for each file. A file may only be selected by at most one ConfigFileEntry - if it is selected by subsequent ones, it
 *  is ignored. ConfigFileEntries are processed in the order they appear in the config file.<br/><br/>
 *  </li>
 *
 *  <li>Locate and parse an optional manifest override file. This is used to override the default behaviour of the ConfigFileEntries
 *  so the end user has more control over the promotion ordering. It is possible to:
 *  <ul>
 *    <li>Override the position of a file by explicitly declaring its position (a "position override")</li>
 *    <li>Force a file to be included in the manifest more than once by explicitly declaring a position and prefixing its
 *    filename with a "+" character (a "forced duplicate"). It will still be included in its default position (or in an overriden
 *    position if this is also specified as above)</li>
 *    <li>Change the loader a file uses, change the values of its default properties, or add new properties using
 *    an "augmentation" entry prefixed with the "~" character</li>
 *  </ul>
 *  Note in the first 2 cases the user is required to always provide a loader name.<br/><br/>
 *  </li>
 *  <li>Locate and parse an optional additional properties file. This should be a file consisting solely of property
 *  augmentation entries for files in the manifest. This should be used to feed additional information from the shell
 *  environment into the manifest build process, for instance the VCS version labels of each file.<br/><br/>
 *  </li>
 *  <li>Coalesce the entries gathered in the steps above into a single list. Properties and loader names can be overloaded
 *  and should cascade in a sensible way, i.e. the values from the most specific entry should take precedence (see
 *  {@link #generateManifestEntryPropertyMap}.</li>
 *  </ol>
 */
public class ManifestBuilder
implements FileResolver {
  
  private static final String BUILDER_CONFIG_FILENAME_PREFIX = "builder";
  private static final String BUILDER_CONFIG_FILENAME_SUFFIX = "\\.cfg"; //this goes into a regex
  
  private static final String OVERRIDE_FILENAME_PREFIX = "manifest-override";
  private static final String OVERRIDE_FILENAME_SUFFIX = "\\.mf"; //this goes into a regex
  
  public static final String PROPERTY_NAME_GENERATED_DATETIME = "manifest_generated_datetime";  
  public static final String PROPERTY_NAME_FILE_VERSION = "file_version";
  public static final String PROPERTY_NAME_FILE_HASH = "file_hash";
   
  private final File mBaseDirectory;
  private final String mPromotionLabel;
  
  /** Files which have already been implicated by a glob in a ConfigFileEntry rule. Note this does NOT necessarily include paths from override entries. */
  private final Set<String> mGlobbedFilePathSet = new HashSet<String>();
  
  /** List of ManifestEntries which are in overriden positions. List should be in manifest order. */
  private final List<ManifestEntry> mManifestEntryPostionOverrideList = new ArrayList<ManifestEntry>();
  
  /** Augmentation manifest entries from the manifest override file  */
  private final Map<String, ManifestEntry> mManifestOverrideAugmentationMap = new HashMap<String, ManifestEntry>();  
  
  /** Augmentation manifest entries from the additional properties file  */
  private final Map<String, ManifestEntry> mAddtionalPropertiesMap = new HashMap<String, ManifestEntry>();
  
  /** Map of every globbed path to a ConfigFileEntry. Should contain the path of every file which was resolved by at least one ConfigFileEntry rule */
  private final Map<String, ConfigFileEntry> mFilePathToConfigFileEntryMap = new HashMap<String, ConfigFileEntry>();
  
  /** Ordered map used to assemble the final result */
  private final TreeMap<Integer, BuilderManifestEntry> mManifestEntryMap = new TreeMap<Integer, BuilderManifestEntry>();
  
  /** Ordered map containing comment strings at corresponding positions */
  private final TreeMap<Integer, String> mLineToCommentMap = new TreeMap<Integer, String>();
  
  /** Property map for the manifest file */
  private final Map<String, String> mPromotionPropertyMap = new HashMap<String, String>();
  
  private final Set<String> mIgnoredFilePaths = new HashSet<String>();
  
  /**
   * Constructs a new ManifestBuilder which will generate a manifest file for files in the given base directory.
   * @param pBaseDirectory Directory to generate manifest for.
   * @param pPromotionLabel Promotion label for the new manifest.
   * @throws ExManifestBuilder If the given file is not a directory.
   */
  public ManifestBuilder(File pBaseDirectory, String pPromotionLabel) 
  throws ExManifestBuilder {
    
    mBaseDirectory = pBaseDirectory;
    if(!mBaseDirectory.isDirectory()){
      throw new ExManifestBuilder(pBaseDirectory.getAbsolutePath() + " is not a directory");
    }    
    mPromotionLabel = pPromotionLabel;    
  }
  
  public File resolveFile(String pFileName)
  throws FileNotFoundException {
    
    File lFile = new File(mBaseDirectory, pFileName);
    if(!lFile.exists()){
      throw new FileNotFoundException(pFileName + " not found in base directory");
    }
    
    return lFile;
  }
    
  @Override
  public File getBaseDirectory() {
    return mBaseDirectory;
  } 
  
  @Override
  public String relativeFilePath(File pFile){
    Path lBaseDirectoryPath = FileSystems.getDefault().getPath(mBaseDirectory.getAbsolutePath());
    Path lFilePath = FileSystems.getDefault().getPath(pFile.getAbsolutePath());
    Path lRelative = lBaseDirectoryPath.relativize(lFilePath);
    return ScriptRunner.normaliseFilePath(lRelative.toString());
  }  
  
  /**
   * Register a globbed file so it won't be globbed again subsequently.
   * @param pFilePath Canonical file path.
   */
  void addGlobbedFile(String pFilePath){
    mGlobbedFilePathSet.add(pFilePath);
  }
  
  /**
   * Tests if the given path has not already been implicated by a previous glob selection.
   * @param pFilePath Canonical file path to test.
   * @return True if the file at the path has not already been selected.
   */
  boolean checkNotAlreadyGlobbed(String pFilePath){
    return !mGlobbedFilePathSet.contains(pFilePath);
  }
  
  /**
   * Declares the corresponding ConfigFileEntry for a file.
   * @param pFilePath File path.
   * @param pConfigEntry ConfigFileEntry.
   */
  void registerPathToConfigEntryMapping(String pFilePath, ConfigFileEntry pConfigEntry){
    if(!mFilePathToConfigFileEntryMap.containsKey(pFilePath)){
      mFilePathToConfigFileEntryMap.put(pFilePath, pConfigEntry);
    }
  }
  
  /**
   * Builds a manifest file and writes it to the given destination. See {@link ManifestBuilder} for details.
   * @param pAdditionalPropertiesFile Additional properties file for providing additional properties to the build process.
   * Can be null.
   * @param pDestination Writer destination for the manifest file.
   * @throws ExManifestBuilder If the build process fails.
   * @throws ExParser If the configuration file cannot be parsed.
   */
  public void buildManifest(File pAdditionalPropertiesFile, PrintWriter pDestination) 
  throws ExManifestBuilder, ExParser {
    
    //Assert the config file exists    
    Collection<File> lConfigFileList = FileUtils.listFiles(
      mBaseDirectory
    , new RegexFileFilter(BUILDER_CONFIG_FILENAME_PREFIX + ".*" + BUILDER_CONFIG_FILENAME_SUFFIX)
    , new NameFileFilter(ScriptRunner.SCRIPTRUNNER_DIRECTORY_NAME)
    );
    
    File lConfigFile;
    if(lConfigFileList.size() != 1){
      throw new ExManifestBuilder("Exactly one builder[*].cfg file should be specified but found " + lConfigFileList.size());
    }
    else {      
      lConfigFile = lConfigFileList.iterator().next();
      Logger.logDebug("Found builder config file called " + lConfigFile.getName());
    }    
    
    //Attempt to locate the manifest override 
    Collection<File> lManifestOverrideFileList = FileUtils.listFiles(
      mBaseDirectory
    , new RegexFileFilter(OVERRIDE_FILENAME_PREFIX + ".*" + OVERRIDE_FILENAME_SUFFIX)
    , new NameFileFilter(ScriptRunner.SCRIPTRUNNER_DIRECTORY_NAME)
    );
    
    if(lManifestOverrideFileList.size() > 1){
      throw new ExManifestBuilder("Only one manifest override can be specified, found " + lManifestOverrideFileList.size());
    }
    
    //Parse the manifest override if it exists
    ManifestParser lOverrideParser = null;
    if(lManifestOverrideFileList.size() == 1){      
      lOverrideParser = parseManifestOverride(lManifestOverrideFileList.iterator().next());
    }
    
    //Parse additional properties if specified
    if(pAdditionalPropertiesFile != null){
      parseAdditionalPropertiesFile(pAdditionalPropertiesFile);
    }
    
    //Parse the config file
    ConfigFileParser lConfigParser = new ConfigFileParser(this);
    lConfigParser.parse(lConfigFile);
    
    
    //For each Config Entry, generate all the associated manifest entries
    lConfigParser.generateAllManifestEntries();
    
    //Verify all files implicated in override rules were selected by a config file entry
    validateOverrideFiles();
    
    //Coalesce the processed manifest entries into a final list 
    composeFinalManifestEntryList(lConfigParser);
    
    //Generate the promotion properties map
    generatePromotionProperties(lOverrideParser);
    
    //Serialise the manifest
    ManifestSerialiser lManifestSerialiser = new ManifestSerialiser(this);
    lManifestSerialiser.serialise(pDestination);
  }
  
  /**
   * Constructs the final ordered map of ManifestEntries. Each ConfigFileEntry is iterated through and every implicated
   * file added to the map, numbered according to the numbering options specified in the ConfigFileEntry. Files in 
   * overriden positions are inserted appropriately.
   * @param pConfigParser Parser used to parse the config file.
   * @throws ExManifestBuilder If there is a problem with ordering.
   */
  private void composeFinalManifestEntryList(ConfigFileParser pConfigParser) 
  throws ExManifestBuilder {
    
    int lCurrentSequencePosition = 0;
    int lPreviousStart = 0;
    
    for(ConfigFileEntry lConfigFileEntry : pConfigParser.getConfigFileEntryList()){
      
      lCurrentSequencePosition = lPreviousStart + lConfigFileEntry.getStartOffset();
      lPreviousStart = lCurrentSequencePosition;
      
      mLineToCommentMap.put(lCurrentSequencePosition, "\n# Files for: " + lConfigFileEntry.getWildcardFilePath() + " (start at " + lCurrentSequencePosition + ")\n");
      
      //Get any overriden files before this position
      processPrecedingPositionOverrides(lCurrentSequencePosition);
      
      for(BuilderManifestEntry lManifestEntry : lConfigFileEntry.getEntryList()){
        lCurrentSequencePosition += lConfigFileEntry.getFileOffset();
        //Get any overriden files before this position
        processPrecedingPositionOverrides(lCurrentSequencePosition);
        
        lManifestEntry.setSequencePosition(lCurrentSequencePosition);
        addManifestEntry(lManifestEntry);        
      }
    }
    
    //Add any remaining position overrides
    processPrecedingPositionOverrides(Integer.MAX_VALUE);        
  }
  
  /**
   * Pops entries off the start of the position override list and adds them to the final manifest entry list, up to the given sequence position.
   * @param pPosition Sequence position to process up to.
   * @throws ExManifestBuilder If there is an ordering problem.
   */
  private void processPrecedingPositionOverrides(int pPosition) 
  throws ExManifestBuilder {
    while(mManifestEntryPostionOverrideList.size() > 0){
      ManifestEntry lManifestEntry = mManifestEntryPostionOverrideList.get(0);
      if(lManifestEntry.getSequencePosition() > pPosition){
        //Break out of the loop when the next override in the list is beyond our current position
        return;
      }
      else {
        //Pop the head of the list
        mManifestEntryPostionOverrideList.remove(0);
        
        //Determine the property map for this entry
        Map<String, String> lMap = generateManifestEntryPropertyMap(lManifestEntry.getFilePath(), getConfigFileEntryForPath(lManifestEntry.getFilePath()), lManifestEntry);
        BuilderManifestEntry lNewManifestEntry = ((BuilderManifestEntry) lManifestEntry).clone(lMap);
        
        //TODO manifest debug option
        //mLineToCommentMap.put(lManifestEntry.getSequencePosition(), "# Overriden position " + lManifestEntry.getSequencePosition());
        addManifestEntry(lNewManifestEntry);
      }
    }    
  }
  
  /**
   * Adds an entry to the final manifest entry list. The position of the new entry is validated before adding.
   * @param pBuilderManifestEntry Entry to add.
   * @throws ExManifestBuilder If the position is invalid.
   */
  private void addManifestEntry(BuilderManifestEntry pBuilderManifestEntry) 
  throws ExManifestBuilder {    
    int lSequence = pBuilderManifestEntry.getSequencePosition();    
    
    if(mManifestEntryMap.containsKey(lSequence)){
      throw new ExManifestBuilder("Cannot add " + pBuilderManifestEntry.getFilePath() + " at position " + lSequence + " - an entry already exists for this index");
    }
    else if (pBuilderManifestEntry.isIgnoredEntry()) {
      //Don't put entries with an Ignore loader into the final map - record a comment instead
      mLineToCommentMap.put(lSequence, "# Ignored: " + pBuilderManifestEntry.getFilePath() + " (position " + lSequence + ")");
      //Record the ignored path so we know we hit this file and we don't need to warn about it
      mIgnoredFilePaths.add(pBuilderManifestEntry.getFilePath());
    }
    else {
      mManifestEntryMap.put(lSequence, pBuilderManifestEntry);
    }
    
  }
  
  /**
   * Parses an additional properties file and validates that it only contains property augmentation entries, then populates
   * the corresponding additional properties map.
   * @param pPropertiesFile File to parse.
   * @throws ExManifestBuilder If the file could not be parsed or is invalid.
   */
  private void parseAdditionalPropertiesFile(File pPropertiesFile) 
  throws ExManifestBuilder {
    
    Logger.logDebug("Reading additional properties file " + pPropertiesFile.getName());
    
    ManifestParser lParser = new ManifestParser(pPropertiesFile);
    try {
      lParser.parse();
    }
    catch(Throwable th){
      throw new ExManifestBuilder("Failed to read additional properties file", th);
    }
    
    for(ManifestEntry lEntry : lParser.getManifestEntryList()){
      if(lEntry.isAugmentation()){
        if(!"".equals(lEntry.getLoaderName())){
          throw new ExManifestBuilder("Error in entry for " + lEntry.getFilePath() + ". Loader names cannot be specified in the additional properties file");
        }
        mAddtionalPropertiesMap.put(lEntry.getFilePath(), lEntry);
      }      
      else {
        throw new ExManifestBuilder("Only augmentation entries can be specified in the additional properties file");
      }
    }
    
  }
  
  /**
   * Parses and validates a manifest override file. Augmentation entries are added to the property augmentation map
   * and forced duplicate/position override entries are added to the position override list.
   * @param pOverrideFile File to be parsed.
   * @return The parser which was used to parse the manifest override.
   * @throws ExManifestBuilder If the file cannot be parsed or is invalid.
   */
  private ManifestParser parseManifestOverride(File pOverrideFile) 
  throws ExManifestBuilder {
    
    Logger.logDebug("Located manifest override file " + pOverrideFile.getName());
    
    //Parse the manifest override file
    ManifestParser lParser = new ManifestParser(pOverrideFile);
    try {
      lParser.parse();
    }
    catch(Throwable th){
      throw new ExManifestBuilder("Failed to parse manifest override file", th);
    }
    
    //Use a TreeMap to order the manifest entries with overriden positions
    TreeMap<Integer, ManifestEntry> lPositionOverrideMap = new TreeMap<Integer, ManifestEntry>();
    
    for(ManifestEntry lEntry : lParser.getManifestEntryList()){
      if(lEntry.isAugmentation()){
        //If this is an augmentation entry, add it to the augmentation entry map
        if(mManifestOverrideAugmentationMap.containsKey(lEntry.getFilePath())){
          throw new ExManifestBuilder("File " + lEntry.getFilePath() + " has more than one augmentation (~) entry - only 1 is allowed per file");
        }
        mManifestOverrideAugmentationMap.put(lEntry.getFilePath(), lEntry);
      }
      else {
        //Otherwise, this is a position override entry or forced duplicate entry - add it to the position override map
        lPositionOverrideMap.put(lEntry.getSequencePosition(), lEntry);
        //If this is not a forced duplicate, add it to the "globbed" set so it is no re-processed by config file entry rules
        if(!lEntry.isForcedDuplicate()){
          mGlobbedFilePathSet.add(lEntry.getFilePath());
        }
      }
    }
    
    //Add map values to list in correct order
    mManifestEntryPostionOverrideList.addAll(lPositionOverrideMap.values());
    
    return lParser;
  }
  
  /**
   * Determines the name of the loader which should be given to a manifest entry. This could be specified in an 
   * augmentation entry, otherwise the default from the config entry will be used.
   * @param pFilePath Canonical file path to determine loader name for.
   * @param pConfigEntry ConfigEntry to use for default.
   * @return A loader name.
   */
  String manifestEntryLoaderName(String pFilePath, ConfigFileEntry pConfigEntry){
    ManifestEntry lEntry = mManifestOverrideAugmentationMap.get(pFilePath);
    //The augmentation entry may not specify a loader name, in which case use the default
    if(lEntry != null && !"".equals(lEntry.getLoaderName())){
      return lEntry.getLoaderName();
    }
    else {
      return pConfigEntry.getLoaderName();
    }
  }
  
  /**
   * Populates the given map with the set of default properties which are given to every file. Currently this includes
   * the file hash.
   * @param pPropertyMap Map to populate.
   * @param pFilePath Canonical file path.
   */
  private void populateDefaultProperties(Map<String, String> pPropertyMap, String pFilePath){
    try {
      pPropertyMap.put(PROPERTY_NAME_FILE_HASH, ManifestEntry.hashFile(pFilePath, this));
    }
    catch (IOException e) {
      throw new ExFatalError("Failed to generate file hash for file " + pFilePath, e);
    }
  }
  
  /**
   * Gets the corresponding ConfigFileEntry for a given file path. This provides a reverse mapping of file paths
   * to ConfigFileEntries (i.e. glob rules).
   * @param pFilePath Canonical file path.
   * @return The file's ConfigFileEntry.
   */
  private ConfigFileEntry getConfigFileEntryForPath(String pFilePath){
    ConfigFileEntry lConfigEntry = mFilePathToConfigFileEntryMap.get(pFilePath);
    if(lConfigEntry == null){
      //Shouldn't happen - indicates a programming error
      throw new ExInternal("Config entry would have been null for " + pFilePath);
    }
    return lConfigEntry;
  }  
  
  /**
   * Generates a property map for a file. Property overloading may cascade in the following order, from lowest to highest
   * precedence:
   * <ol>
   * <li>Default properties from the ConfigFileEntry</li>
   * <li>Any properties from the additional properties file</li>
   * <li>Any properties from augmentation entries in the manifest override file</li>
   * <li>Properties from the ManifestEntry if it has already been created (i.e. it is a position override entry)</li>
   * </ol>
   * Default properties such as the file hash are always set here and cannot be overloaded.
   * @param pFilePath File path to generate properies for.
   * @param pConfigEntry Corresponding ConfigFileEntry for the file.
   * @param pForManifestEntry An existing ManifestEntry to take properties from. Can be null.
   * @return A new property map.
   */
  Map<String, String> generateManifestEntryPropertyMap(String pFilePath, ConfigFileEntry pConfigEntry, ManifestEntry pForManifestEntry){
    
    Map<String, String> lResult = new TreeMap<String, String>();
        
    //1) Start off with properties specified in the config entry for this file
    lResult.putAll(pConfigEntry.getPropertyMap());
    
    //2) check for entries in additional properties
    ManifestEntry lAdditionalPropertiesEntry = mAddtionalPropertiesMap.get(pFilePath);
    if(lAdditionalPropertiesEntry != null){
      lResult.putAll(lAdditionalPropertiesEntry.getPropertyMap());
    }
    
    //3) check for augmentation entries in manifest override
    ManifestEntry lAugmentationEntry = mManifestOverrideAugmentationMap.get(pFilePath);
    if(lAugmentationEntry != null){
      lResult.putAll(lAugmentationEntry.getPropertyMap());
    }
    
    //4) If this is an override entry, take the overriden properties from that entry
    if(pForManifestEntry != null){
      lResult.putAll(pForManifestEntry.getPropertyMap());
    }
    
    //5) Add default properties which all files have
    //   There should be no overloading here as assertions should have been made elsewhere that these properties were not already specified.
    populateDefaultProperties(lResult, pFilePath);
    
    return lResult;
  }
  
  /**
   * Validates that all file paths which are implicated by override rules are files which would have been selected by
   * a configuration file rule.
   * @throws ExManifestBuilder If any invalid file paths are specified in override rules.
   */
  private void validateOverrideFiles()
  throws ExManifestBuilder {
    //Check both position overrides and property augmentations 
    Set<ManifestEntry> lCombinedSet = new HashSet<ManifestEntry>();
    lCombinedSet.addAll(mManifestEntryPostionOverrideList);
    lCombinedSet.addAll(mManifestOverrideAugmentationMap.values());
    
    //Check override entries
    for(ManifestEntry lEntry : lCombinedSet){
      String lPath = lEntry.getFilePath();
      if(!mFilePathToConfigFileEntryMap.containsKey(lPath)){
        throw new ExManifestBuilder("File '" + lPath + "' is implicated in a manifest override entry but not selected by any rules in the configuration file, so cannot be overriden");
      }
    }
    
    //Check additional properties entries
    for(ManifestEntry lEntry : mAddtionalPropertiesMap.values()){
      String lPath = lEntry.getFilePath();
      if(!mFilePathToConfigFileEntryMap.containsKey(lPath)){
        throw new ExManifestBuilder("File '" + lPath + "' is implicated in the additional properties file but not selected by any rules in the configuration file, so cannot be overriden");
      }
    }
  }
  
  /**
   * Generates the promotion property map for this manifest builder. Properties can be merged in from an optional manifest
   * override parser.
   * @param pOverrideParser A parser which has parsed a manifest override file. Can be null.
   */
  private void generatePromotionProperties(ManifestParser pOverrideParser){
    if(pOverrideParser != null && pOverrideParser.getPromotionPropertyMap() != null){
      mPromotionPropertyMap.putAll(pOverrideParser.getPromotionPropertyMap());
    }
    
    //Default properties which are in every promotion and cannot be overwritten
    mPromotionPropertyMap.put(ManifestParser.PROMOTION_LABEL_PROPERTY, mPromotionLabel);    
    mPromotionPropertyMap.put(ManifestParser.SCRIPTRUNNER_VERSION_PROPERTY, ScriptRunnerVersion.getVersionNumber());
    mPromotionPropertyMap.put(PROPERTY_NAME_GENERATED_DATETIME, new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
  }
  
  /**
   * Gets the property map for this manifest file.
   * @return Promotion property map.
   */
  Map<String, String> getPromotionPropertyMap(){
    return mPromotionPropertyMap;
  }
  
  /**
   * Gets the final ordered map of manifest entries in this builder. This requires populating by {@link #buildManifest}.
   * @return Final ManifestEntry map.
   */
  TreeMap<Integer, BuilderManifestEntry> getManifestEntryMap() {
    return mManifestEntryMap;
  }
  
  /**
   * Gets an ordered map of sequence positions and the corresponding comment strings which should be inserted before them.
   * @return Comment map.
   */
  TreeMap<Integer, String> getLineToCommentMap() {
    return mLineToCommentMap;
  }  
  
  /**
   * Set of all relative file paths implicated by at least one manifest entry.
   * @param pIncludeIgnored If true, files with an "Ignore" entry are included. If false, only files which are not "Ignored"
   * are included.
   * @return Set of implicated file paths.
   */
  public Set<String> allImplicatedFilePaths(boolean pIncludeIgnored){
    Set<String> lResultSet = new HashSet<String>();
    for(BuilderManifestEntry lEntry : mManifestEntryMap.values()){
      lResultSet.add(lEntry.getFilePath());
    }
    
    if(pIncludeIgnored) {
      //Bring in any files which were hit by an "Ignore" loader
      lResultSet.addAll(mIgnoredFilePaths);
    }
    return lResultSet;
  }
}
