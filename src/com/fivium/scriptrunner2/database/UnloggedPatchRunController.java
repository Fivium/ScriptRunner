package com.fivium.scriptrunner2.database;

import com.fivium.scriptrunner2.script.ScriptExecutable;

/**
 * Controller for a completely unlogged patch run. This is used by the {@link com.fivium.scriptrunner2.install.Installer Installer}
 * when no logging tables are available.
 */
public class UnloggedPatchRunController 
extends PatchRunController {
  
  public UnloggedPatchRunController() {
    super(null,null,null);
  }

  @Override
  public boolean validateAndStartPatchRun() {
    return true;
  }

  @Override
  public boolean validateAndStartScriptExecutable(ScriptExecutable pPScriptExecutable, int pPStatementSequence) {
    return true;
  }
  
  @Override
  public void endPatchRun(boolean pPWasSuccess) {
    //do nothing
  }

  @Override
  public void endPatchRunStatement(ScriptExecutable pPScriptExecutable, boolean pPWasSuccess) {
    //do nothing
  }

}
