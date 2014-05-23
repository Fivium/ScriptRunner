package com.fivium.scriptrunner2.builder;


import com.fivium.scriptrunner2.ex.ExInternal;
import com.fivium.scriptrunner2.ex.ExParser;
import com.fivium.scriptrunner2.util.XFUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * Parser for a ManifestBuilder config file. This constructs a list of {@link ConfigFileEntry}s based on the contents
 * of the content file.
 */
public class ConfigFileParser {
  
  private final ManifestBuilder mManifestBuilder;
  private final List<ConfigFileEntry> mConfigFileEntryList = new ArrayList<ConfigFileEntry>();
  
  private static final String CONFIG_INSTRUCTION_FILES = "Files";
  private static final String CONFIG_INSTRUCTION_LOADER = "Loader";
  private static final String CONFIG_INSTRUCTION_START_OFFSET = "StartOffset";
  private static final String CONFIG_INSTRUCTION_FILE_OFFSET = "FileOffset";
  private static final String CONFIG_INSTRUCTION_PROPERTIES = "Properties";  
  
  /**
   * Constructs a new ConfigFileParser assoicated with the given ManifestBuilder.
   * @param pManifestBuilder
   */
  public ConfigFileParser(ManifestBuilder pManifestBuilder) {
    mManifestBuilder = pManifestBuilder;
  }
  
  /**
   * Parses the given config file into a list of ConfigFileEntries.
   * @param pConfigFile File to parse.
   * @throws ExParser If the file could not be parsed.
   */
  public void parse(File pConfigFile) 
  throws ExParser {    
   
    try {
      BufferedReader lReader = new BufferedReader(new FileReader(pConfigFile));
      
      String lFilesInstruction = null;
      Map<String, String> lInstructionMap = new HashMap<String, String>();
      int lLineNumber = 0;
      
      //Read lines until a Files: instruction is found (at which point, create a new ConfigFileEntry with all the previously read lines)
      String lLine;
      while((lLine = lReader.readLine()) != null){
        lLineNumber++;
        
        lLine = lLine.trim();        
        if("".equals(lLine) || lLine.startsWith("#")){
          //skip empty lines and comment lines
          continue;
        }
        
        //Validate the line
        if(lLine.indexOf(":") == -1){
          throw new ExParser("Invalid line " + lLineNumber + ", must be in format Instruction: Value");
        }
        
        String lInstruction = lLine.substring(0, lLine.indexOf(":"));
        String lValue = lLine.substring(lLine.indexOf(":") + 1).trim();
        if(lValue.length() == 0){
          throw new ExParser("Invalid line " + lLineNumber + ", value must be specified");
        }
        
        if(CONFIG_INSTRUCTION_FILES.equals(lInstruction)){
          if(!lInstructionMap.isEmpty()){
            //If this isn't the first Files: line we've hit
            createEntry(lFilesInstruction, lInstructionMap);          
            lInstructionMap.clear();
          }
          lFilesInstruction = lValue;
        }
        else {
          lInstructionMap.put(lInstruction, lValue);
        }
        
      }
      
      //Create an entry with the data from the final read (flush the buffer)
      createEntry(lFilesInstruction, lInstructionMap);
    }
    catch (FileNotFoundException e) {
      throw new ExInternal("Failed to read config file", e);
    }
    catch (IOException e) {
      throw new ExInternal("Failed to read config file", e);
    }    
    
  }  

  /**
   * Creates a ConfigFileEntry and adds it to the list.
   * @param pFileWildcardPath "Files:" instruction value for the entry.
   * @param pInstructionMap Map of other instructions for the entry.
   * @throws ExParser If parsing fails.
   */
  private void createEntry(String pFileWildcardPath, Map<String, String> pInstructionMap) 
  throws ExParser {
    
    //Get and validate the loader name
    String lLoader = pInstructionMap.remove(CONFIG_INSTRUCTION_LOADER);
    if(XFUtil.isNull(lLoader)){
      throw new ExParser("Invalid config file definition - 'Loader:' section cannot be null or empty");
    }
    
    //Retrieve the other values from the map
    int lStartOffset;
    int lFileOffset;
    try {
      lStartOffset = Integer.parseInt(pInstructionMap.remove(CONFIG_INSTRUCTION_START_OFFSET));
      lFileOffset = Integer.parseInt(pInstructionMap.remove(CONFIG_INSTRUCTION_FILE_OFFSET));
    }
    catch (NumberFormatException e) {
      throw new ExParser("Invalid integer value for Files section " + pFileWildcardPath);
    }
    catch (NullPointerException e){
      throw new ExParser("Invalid Files section " + pFileWildcardPath + " - both  " + CONFIG_INSTRUCTION_START_OFFSET + 
                         " and " + CONFIG_INSTRUCTION_FILE_OFFSET + " instructions must be specified");
    }    
    String lProperties = pInstructionMap.remove(CONFIG_INSTRUCTION_PROPERTIES);
    
    if(pInstructionMap.size() > 0){
      throw new ExParser("Unrecognised config file instruction: " + pInstructionMap.keySet().iterator().next());
    }
    
    mConfigFileEntryList.add(new ConfigFileEntry(mManifestBuilder, pFileWildcardPath, lLoader, lStartOffset, lFileOffset, lProperties));
  }
  
  /**
   * Iterates through each ConfigFileEntry in this parser's list and generates the associated ManifestEntries for each one.
   */
  public void generateAllManifestEntries(){
    for(ConfigFileEntry lEntry : mConfigFileEntryList){
      lEntry.generateManifestEntries();
    }
  }
  
  /**
   * Returns the list of parsed ConfigFileEntries.
   * @return List of ConfigFileEntries.
   */
  public List<ConfigFileEntry> getConfigFileEntryList(){
    return mConfigFileEntryList;
  }
  
}
