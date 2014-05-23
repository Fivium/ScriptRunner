package com.fivium.scriptrunner2.ex;

/**
 * Exception class for errors caused by preparing a {@link com.fivium.scriptrunner2.loader.Loader Loader}.
 */
public class ExLoader 
extends ExRoot {
  
  public ExLoader(Throwable pThrowable) {
    super(pThrowable);
  }

  public ExLoader(String pString, Throwable pThrowable) {
    super(pString, pThrowable);
  }

  public ExLoader(String pString) {
    super(pString);
  }

  public ExLoader() {
    super();
  }
}
