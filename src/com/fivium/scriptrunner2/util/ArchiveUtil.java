package com.fivium.scriptrunner2.util;


import com.fivium.scriptrunner2.ex.ExFatalError;
import com.fivium.scriptrunner2.ex.ExInternal;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import java.util.Collection;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.IOUtils;


/**
 * Utility class for dealing with zip archives.
 */
public class ArchiveUtil {
  private ArchiveUtil() {}
  
  /**
   * Extracts the contents of a zip file into a folder.<br/><br/>
   *
   * Based on a solution provided by NeilMonday on <a href="http://stackoverflow.com/questions/981578/how-to-unzip-files-recursively-in-java">Stack Overflow</a>.
   * @param pZipFile Zip File to extract.
   * @param pDestinationDirectory Directory to place extracted files in. If the zip contains "file1.txt" and the destination
   * directory is "Dir1", the file's path when extracted will be "Dir1/file1.txt".
   * @return Number of files extracted from the zip file.
   * @throws ZipException
   * @throws IOException
   */
  static public int extractZipToFolder(File pZipFile, File pDestinationDirectory) 
  throws ZipException, IOException {
    
    if(!pDestinationDirectory.isDirectory()){
      throw new ExInternal(pDestinationDirectory.getAbsolutePath() + " is not a valid directory");
    }
    
    ZipFile lZipFile = new ZipFile(pZipFile);    
    Enumeration<? extends ZipEntry> lZipFileEntries = lZipFile.entries();
    
    int lFileCount = 0;

    // Process each entry
    while(lZipFileEntries.hasMoreElements()) {
      
      // grab a zip file entry
      ZipEntry lEntry = lZipFileEntries.nextElement();
      String lCurrentEntry = lEntry.getName();        
      File lDestFile = new File(pDestinationDirectory, lCurrentEntry);      
      File lDestinationParent = lDestFile.getParentFile();

      // create the parent directory structure if needed
      lDestinationParent.mkdirs();

      if(!lEntry.isDirectory()) {
        lFileCount++;
        InputStream lInput = lZipFile.getInputStream(lEntry);
        FileOutputStream lOutput =  new FileOutputStream(lDestFile);
        IOUtils.copy(lInput, lOutput);
        lInput.close();
        lOutput.flush();
        lOutput.close();
      }
    }
    
    return lFileCount;
  }
  
  /**
   * Creates a zip file of all the files in the given set. The relative path of the file is used to determine its name
   * and location within the archive.
   * @param pBaseDirectory Base directory for relative file path evaluation.
   * @param pRelativeFilePathSet Relative paths of files to be zipped.
   * @param pDestinationFile Destination for writing the zip file to.
   */
  public static void createZip(File pBaseDirectory, Collection<String> pRelativeFilePathSet, File pDestinationFile) {

    try {
      ZipOutputStream lZipOutput = new ZipOutputStream(new FileOutputStream(pDestinationFile));
      for (String lPath : pRelativeFilePathSet) {
        addToZip(pBaseDirectory, lPath, lZipOutput);
      }
      lZipOutput.close();
    } 
    catch (IOException e) {
      throw new ExFatalError("Failed to create zip file", e);
    }
  }
  
  /**
   * Adds a file to a ZipOutputStream.
   * @param pBaseDirectory Base directory for relative file path evaluation.
   * @param pRelativeFilePath Path to file within base directory.
   * @param pOutputStream ZipOutputStream destintation.
   * @throws FileNotFoundException If the file could not be found.
   * @throws IOException If the file could not be added to the zip.
   */
  private static void addToZip(File pBaseDirectory, String pRelativeFilePath, ZipOutputStream pOutputStream) 
  throws FileNotFoundException, IOException {

    FileInputStream lFIS = new FileInputStream(new File(pBaseDirectory, pRelativeFilePath));

    ZipEntry lZipEntry = new ZipEntry(pRelativeFilePath);
    pOutputStream.putNextEntry(lZipEntry);

    IOUtils.copy(lFIS, pOutputStream);

    pOutputStream.closeEntry();
    lFIS.close();
  }
  
}
