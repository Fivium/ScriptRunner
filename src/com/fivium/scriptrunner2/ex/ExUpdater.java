package com.fivium.scriptrunner2.ex;

/**
 * Exception class for errors caused by the ScriptRunner installer.
 */
public class ExUpdater extends ExRoot {
  public ExUpdater() {
    super();
  }

  public ExUpdater(String pString) {
    super(pString);
  }

  public ExUpdater(String pString, Throwable pThrowable) {
    super(pString, pThrowable);
  }

  public ExUpdater(Throwable pThrowable) {
    super(pThrowable);
  }
}
