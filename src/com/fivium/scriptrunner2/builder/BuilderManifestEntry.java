package com.fivium.scriptrunner2.builder;


import com.fivium.scriptrunner2.ManifestEntry;
import com.fivium.scriptrunner2.ex.ExInternal;
import com.fivium.scriptrunner2.loader.BuiltInLoader;

import java.util.Map;

/**
 * A ManifestEntry which is being used by a builder during manifest construction. This subclass has a mutable sequence
 * position.
 */
public class BuilderManifestEntry 
extends ManifestEntry {
  
  /** This cannot be final as it is not known at construction time */
  private int mSequencePosition = 0;
  
  public BuilderManifestEntry(boolean pIsAugmentation, String pFilePath, String pLoaderName, int pSequence, Map<String, String> pPropertyMap, boolean pIsForcedDuplicate){
    this(pIsAugmentation, pFilePath, pLoaderName, pPropertyMap, pIsForcedDuplicate);    
    mSequencePosition = pSequence;    
  }
  
  public BuilderManifestEntry(boolean pIsAugmentation, String pFilePath, String pLoaderName, Map<String, String> pPropertyMap, boolean pIsForcedDuplicate){
    super(pIsAugmentation, pFilePath, pLoaderName, pPropertyMap, pIsForcedDuplicate); 
    //Validate loader name
    if(!pIsAugmentation && "".equals(getLoaderName())){
      throw new ExInternal("Non-augmentation entry cannot have an empty loader name");
    }
  }
  
  @Override
  public int getSequencePosition(){
    return mSequencePosition;
  }  

  public void setSequencePosition(int pSequencePosition) {
    mSequencePosition = pSequencePosition;
  }  
  
  public boolean isIgnoredEntry() {
    return BuiltInLoader.LOADER_NAME_IGNORE.equals(getLoaderName());
  }  
  
  /**
   * Clones this object. The cloned object will have the specified new properties.
   * @param pNewProperties New properties for the cloned object.
   * @return The new object.
   */
  public BuilderManifestEntry clone(Map<String, String> pNewProperties){    
    return new BuilderManifestEntry(isAugmentation(), getFilePath(), getLoaderName(), mSequencePosition, pNewProperties, isForcedDuplicate());
  }

}
