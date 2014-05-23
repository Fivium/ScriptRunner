package com.fivium.scriptrunner2.ex;

/**
 * Exception class for errors encountered when building a manifest file.
 */
public class ExManifestBuilder 
extends ExRoot {
  public ExManifestBuilder() {
    super();
  }

  public ExManifestBuilder(String pString) {
    super(pString);
  }

  public ExManifestBuilder(String pString, Throwable pThrowable) {
    super(pString, pThrowable);
  }

  public ExManifestBuilder(Throwable pThrowable) {
    super(pThrowable);
  }
}
