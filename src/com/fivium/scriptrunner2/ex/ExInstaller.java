package com.fivium.scriptrunner2.ex;

/**
 * Exception class for errors caused by the ScriptRunner installer.
 */
public class ExInstaller extends ExRoot {
  public ExInstaller() {
    super();
  }

  public ExInstaller(String pString) {
    super(pString);
  }

  public ExInstaller(String pString, Throwable pThrowable) {
    super(pString, pThrowable);
  }

  public ExInstaller(Throwable pThrowable) {
    super(pThrowable);
  }
}
