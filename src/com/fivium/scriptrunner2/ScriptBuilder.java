package com.fivium.scriptrunner2;


import com.fivium.scriptrunner2.builder.ManifestBuilder;
import com.fivium.scriptrunner2.ex.ExFatalError;
import com.fivium.scriptrunner2.ex.ExManifestBuilder;
import com.fivium.scriptrunner2.ex.ExParser;
import com.fivium.scriptrunner2.util.ArchiveUtil;
import com.fivium.scriptrunner2.util.XFUtil;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;

import java.util.Collection;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;


/**
 * Class for co-ordinating the creation of a promotion archive file.
 */
public class ScriptBuilder {
  
  /**
   * Generates a promotion archive by creating a manifest and zipping up all the manifest's implicated files into a single
   * file. All options for this procedure should be specified on the command line.
   * @param pOptionWrapper All command line options.
   * @throws ExManifestBuilder If the manifest cannot be built.
   * @throws ExParser If the manifest override or additional properties cannot be parsed.
   * @throws ExFatalError If required command line options are missing or invalid.
   */
  public static void run(CommandLineWrapper pOptionWrapper) 
  throws ExManifestBuilder, ExParser {
    
    //Determine source directory
    
    String lSourceDirectoryString = pOptionWrapper.getOption(CommandLineOption.BUILD);
    if(XFUtil.isNull(lSourceDirectoryString)){
      throw new ExFatalError("-" + CommandLineOption.BUILD.getArgString() + " argument must be specified");
    }
    Logger.logInfo("Source directory is " + lSourceDirectoryString);
    
    File lSourceDirectory = new File(lSourceDirectoryString); 
    
    //Determine promotion label
    
    String lPromotionLabel = pOptionWrapper.getOption(CommandLineOption.PROMOTION_LABEL);
    if(XFUtil.isNull(lPromotionLabel)){
      throw new ExFatalError("-" + CommandLineOption.PROMOTION_LABEL.getArgString() + " argument must be specified");
    }
    Logger.logInfo("Promotion label is " + lPromotionLabel);
    
    //Determine output location
    
    String lOutputFileString = pOptionWrapper.getOption(CommandLineOption.OUTPUT_FILE_PATH);
    File lOutputFile;
    if(XFUtil.isNull(lOutputFileString)){
      lOutputFile = new File(new File(System.getProperty("user.dir")), lPromotionLabel + ".zip");
    }
    else {
      lOutputFile = new File(lOutputFileString);      
    }
    
    //Determine additional properties file location
    
    String lAdditionalPropsPath = pOptionWrapper.getOption(CommandLineOption.ADDITIONAL_PROPERTIES);
    File lAdditionalPropsFile = null;
    if(!XFUtil.isNull(lAdditionalPropsPath)){
      lAdditionalPropsFile = new File(lAdditionalPropsPath);
      if(!lAdditionalPropsFile.exists()){
        throw new ExFatalError("Additional properties file not found at " + lAdditionalPropsPath);
      }
    }
    
    File lManifestDestinationFile = new File(lSourceDirectory, ScriptRunner.MANIFEST_RELATIVE_FILE_PATH);
    //Ensure the /ScriptRunner leg exists (it always should)
    lManifestDestinationFile.getParentFile().mkdirs();
    PrintWriter lManifestDestinationWriter;
    try {
      lManifestDestinationWriter = new PrintWriter(lManifestDestinationFile);
    }
    catch (FileNotFoundException e) {
      throw new ExFatalError("Cannot create manifest file " + lManifestDestinationFile.getAbsolutePath(), e);
    }
          
    //Construct the manifest file
    ManifestBuilder lManifestBuilder = new ManifestBuilder(lSourceDirectory, lPromotionLabel);
    lManifestBuilder.buildManifest(lAdditionalPropsFile, lManifestDestinationWriter);
    
    //Check that all files in the source directory have been implicated
    Set<String> lImplicatedManifestFilePaths = lManifestBuilder.allImplicatedFilePaths(true);
    Set<String> lFilePathsInBaseDirectory = ScriptRunner.allFilePathsInBaseDirectory(lManifestBuilder);
    
    lFilePathsInBaseDirectory.removeAll(lImplicatedManifestFilePaths);
    
    if(lFilePathsInBaseDirectory.size() > 0){
      String warningMessage = lFilePathsInBaseDirectory.size() + " files found in source directory but not implicated by manifest builder rules:";
      if(pOptionWrapper.hasOption(CommandLineOption.NO_UNIMPLICATED_FILES)){
        StringBuilder errorMessage = new StringBuilder();
        errorMessage.append(warningMessage + "\n");
        for (String lPath : lFilePathsInBaseDirectory) {
          errorMessage.append(lPath + "\n");
        }
        throw new ExManifestBuilder(errorMessage.toString());
      } else {
        Logger.logWarning(warningMessage);
        for (String lPath : lFilePathsInBaseDirectory) {
          Logger.logInfo(lPath);
        }
      }
    }
    
    //Get all non-ignored files to put in the zip
    lImplicatedManifestFilePaths = lManifestBuilder.allImplicatedFilePaths(false);
    
    //Get all files in the /ScriptRunner leg
    Collection<File> lScripRunnerFileList = FileUtils.listFiles(new File(lSourceDirectory, ScriptRunner.SCRIPTRUNNER_DIRECTORY_NAME), TrueFileFilter.INSTANCE, TrueFileFilter.INSTANCE);
    for(File lFile : lScripRunnerFileList){
      lImplicatedManifestFilePaths.add(lManifestBuilder.relativeFilePath(lFile));
    }    
    
    Logger.logAndEcho("Building promotion file " + lOutputFile.getAbsolutePath());
    
    //Create the final archive in the output location
    ArchiveUtil.createZip(lSourceDirectory, lImplicatedManifestFilePaths, lOutputFile);
    
  }
  
}
