package com.fivium.scriptrunner2.loader;

import com.fivium.scriptrunner2.PromotionFile;
import com.fivium.scriptrunner2.ScriptRunner;
import com.fivium.scriptrunner2.ex.ExPromote;

/**
 * A Loader is used to promote a file to a database.
 */
public interface Loader {

  /**
   * Promotes a file to the database.
   * @param pScriptRunner ScriptRunner performing the promotion.
   * @param pPromotionFile File to be promoted.
   * @throws ExPromote If the promotion fails.
   */
  public void promoteFile(ScriptRunner pScriptRunner, PromotionFile pPromotionFile)
  throws ExPromote; //manifest file, scriptrunner/db details
  
}
