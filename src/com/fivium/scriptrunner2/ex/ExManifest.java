package com.fivium.scriptrunner2.ex;

/**
 * Exception class for any errors encountered when parsing a manifest file.
 */
public class ExManifest
extends ExRoot {
  
  public ExManifest(Throwable pThrowable) {
    super(pThrowable);
  }

  public ExManifest(String pString, Throwable pThrowable) {
    super(pString, pThrowable);
  }

  public ExManifest(String pString) {
    super(pString);
  }

  public ExManifest() {
    super();
  }
}
