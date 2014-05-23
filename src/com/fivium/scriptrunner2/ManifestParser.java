package com.fivium.scriptrunner2;


import com.fivium.scriptrunner2.ex.ExManifest;
import com.fivium.scriptrunner2.ex.ExParser;
import com.fivium.scriptrunner2.loader.BuiltInLoader;
import com.fivium.scriptrunner2.loader.MetadataLoader;

import com.google.common.base.Splitter;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Parser for parsing either a Manifest file, Manifest override file, or additional properties file. This class provides
 * functionality common to all three use cases. The {@link PromotionManifestParser} subclass provides additional
 * validation for parsing a Manifest file which will be used to perform a promotion.
 */
public class ManifestParser {
       
  /**
   * Pattern to match a PROMOTION instruction.
   * Line syntax: "PROMOTION PromotionName"
   */
  private static final Pattern MANIFEST_LINE_PROMOTION_PROPERTIES_PATTERN = Pattern.compile("^PROMOTION[ \\t]+(\\{.+\\})$");
  
  public static final String PROMOTION_LABEL_PROPERTY = "promotion_label";
  public static final String SCRIPTRUNNER_VERSION_PROPERTY = "scriptrunner_version";  
  
  private final File mManifestFile;
  protected final Map<String, MetadataLoader> mLoaderMap = new HashMap<String, MetadataLoader>();
  protected final List<ManifestEntry> mManifestEntryList = new ArrayList<ManifestEntry>();  
  protected Map<String, String> mPromotionPropertyMap = null;
  
  /** A set of all file paths for manifest entries which were processed during a parse and were NOT forced duplicates */
  protected final Set<String> mFinalProcessedFilePathSet = new HashSet<String>();
  
  /**
   * Constructs a new ManifestParser which will be used to parse the given file.
   * @param pManifestFile File to be parsed.
   */
  public ManifestParser(File pManifestFile){
    mManifestFile = pManifestFile;    
  }
  
  /**
   * Parses a manifest file (or manifest override file) and populates this parser's list of ManifestEntries.
   * @throws FileNotFoundException If the manifest file does not exist.
   * @throws IOException If the manifest file cannot be read.
   * @throws ExParser If a line cannot be parsed.
   * @throws ExManifest If a ManifestEntry is invalid in the context of the manifest.
   */
  public void parse()
  throws FileNotFoundException, IOException, ExParser, ExManifest {    
    
    BufferedReader lReader = null;
    try {
      lReader = new BufferedReader(new FileReader(mManifestFile));
      String lLine;
      int lHighestSequencePosition = 0;
      
      Map<String, Integer> lFileIndexes = new HashMap<String, Integer>();
      
      LINE_LOOP:
      while((lLine = lReader.readLine())!= null){
        
        //Trim whitespace
        lLine = lLine.trim();
        
        //Skip empty lines
        if("".equals(lLine)){
          continue LINE_LOOP;
        }
        
        //Skip comment lines
        if(lLine.charAt(0) == '#'){
          continue LINE_LOOP;
        }
        
        //Attempt to parse the line as a property line (this will return null if it is not)
        Map<String, String> lPromotionPropertyMap = parsePromotionPropertiesLine(lLine);
        if(lPromotionPropertyMap != null){
          Logger.logDebug("Parsed promotion property line");
          if(mPromotionPropertyMap != null){
            //Only one definition allowed per manifest
            throw new ExParser("Duplicate PROMOTION definition line found");
          }
          mPromotionPropertyMap = lPromotionPropertyMap;
          continue LINE_LOOP;
        }
        
        //Assume any other line is a manifest entry (this will error if it is not)
        ManifestEntry lManifestEntry = parseManifestEntryLine(lLine, lHighestSequencePosition);
        
        lHighestSequencePosition = lManifestEntry.getSequencePosition();
        mManifestEntryList.add(lManifestEntry);
        if(!lManifestEntry.isForcedDuplicate()){
          mFinalProcessedFilePathSet.add(lManifestEntry.getFilePath());
        }
        
        //Record the index of the file
        Integer lFileIndex = lFileIndexes.get(lManifestEntry.getFilePath());
        if(lFileIndex  == null){
          lFileIndex = 1;
        }
        else {
          lFileIndex++;
        }
        lManifestEntry.setFileIndex(lFileIndex);
        lFileIndexes.put(lManifestEntry.getFilePath(), lFileIndex);        
        
        //Construct a new loader if necessary
        createLoaderIfUndefined(lManifestEntry.getLoaderName());        
        
        Logger.logDebug("Parsed manifest entry for " + lManifestEntry.getFilePath());        
      }
    } 
    finally {
      //Close the reader to release the lock on the file
      if(lReader != null){
        lReader.close();
      }
    }
    
  }
  
  /**
   * Parses a line of a manifest file into a ManifestEntry.
   * @param pLine Line of file to be parsed.
   * @param pCurrentSequencePosition
   * @return A new ManifestEntry.
   * @throws ExParser If the line cannot be parsed into a ManifestEntry.
   * @throws ExManifest If the ManifestEntry is invalid in the context of this manifest.
   */
  protected ManifestEntry parseManifestEntryLine(String pLine, int pCurrentSequencePosition)
  throws ExParser, ExManifest  {
    return ManifestEntry.parseManifestFileLine(pLine, false);
  }
  
  /**
   * Returns null if not a prop line
   * @param pLine
   * @return
   * @throws ExParser
   */
  protected Map<String, String> parsePromotionPropertiesLine(String pLine)
  throws ExParser {
    
    Matcher lMatcher = MANIFEST_LINE_PROMOTION_PROPERTIES_PATTERN.matcher(pLine);
    
    if(lMatcher.matches()){
      Map<String, String> lProperties = parsePropertyMapString(lMatcher.group(1));
      return lProperties;
    }
    else {
      return null;
    }
  }
  
  /**
   * Creates a new MetadataLoader in this object's loader map, if required.
   * @param pLoaderName Name of loader to create.
   */
  private void createLoaderIfUndefined(String pLoaderName){
    //Check this is not a duplicate definition or a built-in definition
    if(!mLoaderMap.containsKey(pLoaderName) && BuiltInLoader.getBuiltInLoaderOrNull(pLoaderName) == null){
      //Construct the loader
      MetadataLoader lLoader = new MetadataLoader(pLoaderName);
      //Register in this parser's map
      mLoaderMap.put(pLoaderName, lLoader);
    }     
  }
    
  /**
   * Parses a property map string into a Map of name/value pairs. The required format is as follows:<br/><br/>
   * <code>
   * {name="value", name2="value2"}
   * </code><br/><br/>
   * The string may be null - in this case, an empty map is returned.
   * @param pString Property string to be parsed.
   * @return Map of property names to values.
   * @throws ExParser If the string is not a valid property map string.
   */
  public static Map<String, String> parsePropertyMapString(String pString) 
  throws ExParser {
    
    Map<String, String> lResult = new TreeMap<String, String>();
    
    if(pString != null){
      //Validate string is delimited by { and } markers
      pString = pString.trim();
      if(pString.charAt(0) != '{'){
        throw new ExParser("Property string must start with '{' character");
      }
      else if(pString.charAt(pString.length() -1) != '}'){
        throw new ExParser("Property string must end with '}' character");
      }
      pString = pString.substring(1, pString.length() - 1);
      
      StringBuilder lEscapedString = new StringBuilder();
      //Replace quoted commas with replacement tokens
      //TODO what about quotes in values? (i.e. escape character)
      boolean lInQuotes = false;
      for(int i=0; i<pString.length(); i++){
        char lChar = pString.charAt(i);
        if(lChar=='"') lInQuotes = !lInQuotes;
        if(lInQuotes && lChar==','){
          lEscapedString.append("##COMMA##");
        }
        else if(lInQuotes && lChar=='='){
          lEscapedString.append("##EQUALS##");
        }
        else {
          lEscapedString.append(lChar);
        }
      }
      if(lInQuotes){
        throw new ExParser("Unterminated quotes in property map string");
      }
      
      //Split the whole property string into strings of "name=value" name value pairs - use guava so empty results aren't omitted (these are a parser error)
      Iterable<String> lNameValuePairs = Splitter.on(',').trimResults().split(lEscapedString);
      
      //Loop each name value pair and add the property names as map keys and values as map values
      for(String lNameValuePair : lNameValuePairs){
        String[] lSplit = lNameValuePair.split("[ \\t]*=[ \\t]*");
        
        //There should be exactly 2 elements after splitting on the equals symbol
        if(lSplit.length != 2){
          throw new ExParser("Invalid property string: " + lNameValuePair);
        }
        
        //TODO validate name has no = or , in it
        //Property names are case-insensitive
        String lName = lSplit[0].toLowerCase();      
        //Replace escaped characters back into the value and strip any surrounding quote marks
        String lValue = lSplit[1].replaceAll("##COMMA##", ",").replaceAll("##EQUALS##", "=").replaceAll("\"", "");
                
        //Check that the name does not alredy exist in the map
        if(lResult.containsKey(lName)){
          throw new ExParser("Duplicate property value " + lName);
        }
        
        //Add property to map      
        lResult.put(lName, lValue);
      }
    }
    
    return lResult;
  }
  
  /**
   * Gets a map of names to Loaders constructed by this parser. Note this map will not contain built-in loaders.
   * @return Loader map.
   */
  public Map<String, MetadataLoader> getLoaderMap(){    
    return mLoaderMap;
  }
  
  /**
   * Get the parsed property map for this manifest file. This may be null if the PROMOTION line is not specified.
   * @return Property map.
   */
  public Map<String, String> getPromotionPropertyMap(){
    return mPromotionPropertyMap;
  }
  
  /**
   * Returns a list of all files implicated by this manifest in the order in which they should be promoted.
   * @return Ordered file list.
   */
  public List<ManifestEntry> getManifestEntryList(){
    return mManifestEntryList;
  }
  
}
