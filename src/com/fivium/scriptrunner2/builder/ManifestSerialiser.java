package com.fivium.scriptrunner2.builder;


import com.fivium.scriptrunner2.ex.ExFatalError;

import java.io.PrintWriter;

import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;


/**
 * Serialiser for a {@link ManifestBuilder}. The serialisation process attempts to format the generated manifest file
 * in a human-readable way.
 */
public class ManifestSerialiser {
  
  private final ManifestBuilder mManifestBuilder;
  
  /**
   * Creates a new ManifestSerialiser for the given ManifestBuilder.
   * @param pManifestBuilder ManifestBuilder to be serialised.
   */
  public ManifestSerialiser(ManifestBuilder pManifestBuilder) {
    mManifestBuilder = pManifestBuilder;
  }
  
  /**
   * Serialises the ManifestBuilder to a manifest file at the given destination.
   * @param pDestination Manifest destination.
   */
  public void serialise(PrintWriter pDestination){
    
    if(mManifestBuilder.getManifestEntryMap().size() == 0){
      throw new ExFatalError("Manifest must have at least one entry to be serialised");
    }
    
    //Establish the maximum column width for manifest positions
    int lNumberLength = mManifestBuilder.getManifestEntryMap().lastEntry().getKey().toString().length() + 2;
    
    //Establish the maximum column widths for loader names and file paths
    int lLoaderNameLength = 0;
    int lFilePathLength = 0;
    for(BuilderManifestEntry lEntry : mManifestBuilder.getManifestEntryMap().values()){
      if(lEntry.getLoaderName().length() > lLoaderNameLength){
        lLoaderNameLength = lEntry.getLoaderName().length();
      }
      if(lEntry.getFilePath().length() > lFilePathLength){
        lFilePathLength = lEntry.getFilePath().length();
      }
    }

    //Serialise the promotion properties line    
    pDestination.println("PROMOTION " + serialisePropertyMap(mManifestBuilder.getPromotionPropertyMap()));
    
    //Get the comments which need to be serialised
    TreeMap<Integer, String> lLineToCommentMap = mManifestBuilder.getLineToCommentMap();

    for(BuilderManifestEntry lEntry : mManifestBuilder.getManifestEntryMap().values()){
      
      //Iterate through each comment whose position precedes or equals that of this manifest entry, outputting the comments
      //and removing the entries for subsequent iterations of the ManifestEntry loop
      Iterator<String> lCommentIterator =  lLineToCommentMap.headMap(lEntry.getSequencePosition(), true).values().iterator();
      while(lCommentIterator.hasNext()){
        pDestination.println(lCommentIterator.next());
        lCommentIterator.remove();
      }
      
      //Ouput a formatted string, padding columns to the width of their longest entry      
      pDestination.printf(
        "%0" + lNumberLength + "d:  %-" + lLoaderNameLength + "s   %-1s%-" + lFilePathLength + "s   %s\n"
      , lEntry.getSequencePosition()
      , lEntry.getLoaderName()
      , lEntry.isForcedDuplicate() ? "+" : ""
      , lEntry.getFilePath()
      , serialisePropertyMap(lEntry.getPropertyMap())
      );      
    }
    
    //Print any remaining comments from the map
    for(String lComment : lLineToCommentMap.values()){
      pDestination.println(lComment);
    }
    
    pDestination.flush();    
  }
  
  /**
   * Serialises a name/value property map to a string.
   * @param pPropertyMap Map to be serialised.
   * @return Serialised form of the map.
   */
  private String serialisePropertyMap(Map<String, String> pPropertyMap){
    StringBuilder lResult = new StringBuilder();
    lResult.append("{");
    
    for(Map.Entry<String, String> lEntry : pPropertyMap.entrySet()){      
      lResult.append(lEntry.getKey());
      lResult.append("=");
      lResult.append("\"" + lEntry.getValue() + "\"");
      lResult.append(", ");
    }
    
    lResult.replace(lResult.length() - 2, lResult.length(), "}");
    return lResult.toString();
    
  }
  
}
