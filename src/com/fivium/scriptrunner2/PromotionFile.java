package com.fivium.scriptrunner2;


import com.fivium.scriptrunner2.builder.ManifestBuilder;
import com.fivium.scriptrunner2.util.XFUtil;

import java.util.Map;


/**
 * A PromotionFile encapuslates information about an individual entry in the manifest file. It also contains some stateful
 * information about the file which is populated during the promotion process.
 */
public class PromotionFile
extends ManifestEntry {
  
  private static final String UNKNOWN_VERSION_STRING  = "unknown";
  
  private final int mSequencePosition;  
  
  public PromotionFile(String pFilePath, String pLoaderName, int pSequencePosition, Map<String, String> pPropertyMap, boolean pIsForcedDuplicate){
    super(false, pFilePath, pLoaderName, pPropertyMap, pIsForcedDuplicate);
    mSequencePosition = pSequencePosition;
  }
  
  /** ID of corresponding log row for this file on the database. Only populated just-in-time before the file is promoted. */
  private int mPromotionFileId = -1;  
  
  public void setPromotionFileId(int pPromotionFileId) {
    mPromotionFileId = pPromotionFileId;
  }

  /**
   * Gets the ID of the row corresponding to this file in the <tt>promotion_files</tt> table. Returns -1 if this value
   * is not yet known (which will be the case until the file is actually promoted).
   * @return The database ID for this promotion file.
   */
  public int getPromotionFileId() {
    return mPromotionFileId;
  }

  public int getSequencePosition() {
    return mSequencePosition;
  }
  
  /**
   * Gets the VCS version number of this file, or "unknown" if it was not specified in the manifest.
   * @return File version number or similar identifier.
   */
  public String getFileVersion() {
    return XFUtil.nvl(getPropertyMap().get(ManifestBuilder.PROPERTY_NAME_FILE_VERSION), UNKNOWN_VERSION_STRING);
  }
}
