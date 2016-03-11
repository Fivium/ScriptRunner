package com.fivium.scriptrunner2;


import com.fivium.scriptrunner2.builder.ManifestBuilder;
import com.fivium.scriptrunner2.ex.ExFatalError;
import com.fivium.scriptrunner2.ex.ExManifest;
import com.fivium.scriptrunner2.ex.ExManifestBuilder;
import com.fivium.scriptrunner2.ex.ExParser;
import com.fivium.scriptrunner2.loader.MetadataLoader;
import com.fivium.scriptrunner2.util.ArchiveUtil;
import com.fivium.scriptrunner2.util.XFUtil;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;

import java.util.Collection;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;


/**
 * Class for co-ordinating the creation of a promotion archive file.
 */
public class ScriptBuilder
implements FileResolver {

  /** Container for all command line options which were used to invoke ScriptRunner. */
  private final CommandLineWrapper mCommandLineWrapper;

  /** Source directory of the build. */
  private final File mSourceDirectory;

  /**
   * Creates a new ScriptBuilder.
   * @param pCommandLineWrapper All command line options.
   * @throws ExFatalError If required command line options are missing or invalid.
   */
  public ScriptBuilder(CommandLineWrapper pCommandLineWrapper) {
    mCommandLineWrapper = pCommandLineWrapper;

    //Determine source directory

    String lSourceDirectoryString = mCommandLineWrapper.getOption(CommandLineOption.BUILD);
    if(XFUtil.isNull(lSourceDirectoryString)){
      throw new ExFatalError("-" + CommandLineOption.BUILD.getArgString() + " argument must be specified");
    }
    Logger.logInfo("Source directory is " + lSourceDirectoryString);

    mSourceDirectory = new File(lSourceDirectoryString);
  }
  
  /**
   * Generates a promotion archive by creating a manifest and zipping up all the manifest's implicated files into a single
   * file. All options for this procedure should be specified on the command line.
   * @throws ExManifest If validations of the manifest fail.
   * @throws ExManifestBuilder If the manifest cannot be built.
   * @throws ExParser If the manifest override or additional properties cannot be parsed.
   * @throws ExFatalError If required command line options are missing or invalid.
   */
  public void run()
  throws ExManifest, ExManifestBuilder, ExParser {

    //Determine promotion label
    
    String lPromotionLabel = mCommandLineWrapper.getOption(CommandLineOption.PROMOTION_LABEL);
    if(XFUtil.isNull(lPromotionLabel)){
      throw new ExFatalError("-" + CommandLineOption.PROMOTION_LABEL.getArgString() + " argument must be specified");
    }
    Logger.logInfo("Promotion label is " + lPromotionLabel);
    
    //Determine output location
    
    String lOutputFileString = mCommandLineWrapper.getOption(CommandLineOption.OUTPUT_FILE_PATH);
    File lOutputFile;
    if(XFUtil.isNull(lOutputFileString)){
      lOutputFile = new File(new File(System.getProperty("user.dir")), lPromotionLabel + ".zip");
    }
    else {
      lOutputFile = new File(lOutputFileString);      
    }
    
    //Determine additional properties file location
    
    String lAdditionalPropsPath = mCommandLineWrapper.getOption(CommandLineOption.ADDITIONAL_PROPERTIES);
    File lAdditionalPropsFile = null;
    if(!XFUtil.isNull(lAdditionalPropsPath)){
      lAdditionalPropsFile = new File(lAdditionalPropsPath);
      if(!lAdditionalPropsFile.exists()){
        throw new ExFatalError("Additional properties file not found at " + lAdditionalPropsPath);
      }
    }
    
    File lManifestDestinationFile = new File(mSourceDirectory, ScriptRunner.MANIFEST_RELATIVE_FILE_PATH);
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
    ManifestBuilder lManifestBuilder = new ManifestBuilder(mSourceDirectory, lPromotionLabel);
    lManifestBuilder.buildManifest(lAdditionalPropsFile, lManifestDestinationWriter);
    
    //Check that all files in the source directory have been implicated
    Set<String> lImplicatedManifestFilePaths = lManifestBuilder.allImplicatedFilePaths(true);
    Set<String> lFilePathsInBaseDirectory = ScriptRunner.allFilePathsInBaseDirectory(lManifestBuilder);
    
    lFilePathsInBaseDirectory.removeAll(lImplicatedManifestFilePaths);
    
    if(lFilePathsInBaseDirectory.size() > 0){
      String warningMessage = lFilePathsInBaseDirectory.size() + " files found in source directory but not implicated by manifest builder rules:";
      if(mCommandLineWrapper.hasOption(CommandLineOption.NO_UNIMPLICATED_FILES)){
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

    // If the argument has been specified attempt to parse the manifest
    // and then verify that all the Loader files exist in the source directory.
    if(mCommandLineWrapper.hasOption(CommandLineOption.VERIFY_LOADERS)) {
      //Parse the newly created manifest.
      PromotionManifestParser lParser = new PromotionManifestParser(lManifestDestinationFile);
      try {
        lParser.parse();
      }
      catch (FileNotFoundException e) {
        throw new ExFatalError("Failed to parse manifest: file not found", e);
      }
      catch (IOException e) {
        throw new ExFatalError("Failed to parse manifest: IOException", e);
      }
      catch (ExParser e) {
        throw new ExFatalError("Failed to parse manifest: " + e.getMessage(), e);
      }
      catch (ExManifest e) {
        throw new ExFatalError("Failed to load manifest: " + e.getMessage(), e);
      }

      //Verify all loaders exist
      for (MetadataLoader lLoader : lParser.getLoaderMap().values()) {
        try {
          this.resolveFile(lLoader.getLoaderFilePath());
        }
        catch (FileNotFoundException e) {
          throw new ExManifest("Loader file for loader " + lLoader.getName() + " cannot be located", e);
        }
      }
    }
    
    //Get all non-ignored files to put in the zip
    lImplicatedManifestFilePaths = lManifestBuilder.allImplicatedFilePaths(false);
    
    //Get all files in the /ScriptRunner leg
    Collection<File> lScripRunnerFileList = FileUtils.listFiles(new File(mSourceDirectory, ScriptRunner.SCRIPTRUNNER_DIRECTORY_NAME), TrueFileFilter.INSTANCE, TrueFileFilter.INSTANCE);
    for(File lFile : lScripRunnerFileList){
      lImplicatedManifestFilePaths.add(lManifestBuilder.relativeFilePath(lFile));
    }    
    
    Logger.logAndEcho("Building promotion file " + lOutputFile.getAbsolutePath());
    
    //Create the final archive in the output location
    ArchiveUtil.createZip(mSourceDirectory, lImplicatedManifestFilePaths, lOutputFile);
    
  }

  /**
   * Gets a file from this ScriptRunner's base directory.
   * @param pPath A path to the desired file, relative to the base directory.
   * @return The requested file.
   * @throws FileNotFoundException If the file does not exist.
   */
  public File resolveFile(String pPath)
      throws FileNotFoundException {
    File lFile = new File(mSourceDirectory, pPath);

    if(!lFile.exists()){
      throw new FileNotFoundException("Failed to locate file " + pPath + " in base directory");
    }

    return lFile;
  }

  /**
   * Gets the path of the given file relativised to the current base directory and normalised.
   * @param pFile File to get path of.
   * @return Relativised file path.
   */
  public String relativeFilePath(File pFile){
    return ScriptRunner.normaliseFilePath(mSourceDirectory.toURI().relativize(pFile.toURI()).getPath());
  }

  @Override
  public File getBaseDirectory() {
    return mSourceDirectory;
  }

}
