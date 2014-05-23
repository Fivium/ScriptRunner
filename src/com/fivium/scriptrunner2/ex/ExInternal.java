package com.fivium.scriptrunner2.ex;

/**
 * Exception class for errors which have been caused by internal assertion failures or programming errors.
 */
public class ExInternal
extends ExRuntimeRoot {
  
  public ExInternal(Throwable pThrowable) {
    super(pThrowable);
  }

  public ExInternal(String pString, Throwable pThrowable) {
    super(pString, pThrowable);
  }

  public ExInternal(String pString) {
    super(pString);
  }

  public ExInternal() {
    super();
  }
}
