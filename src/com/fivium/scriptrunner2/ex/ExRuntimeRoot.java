package com.fivium.scriptrunner2.ex;

/**
 * Root runtime exception class for unchecked exceptions.
 */
public class ExRuntimeRoot 
extends RuntimeException {
  
  private void logThis(){
//    Logger.logInfo("ERROR:::");
//    Logger.logError(this);
  }
  
  public ExRuntimeRoot(Throwable pThrowable) {
    super(pThrowable);
    logThis();
  }

  public ExRuntimeRoot(String pString, Throwable pThrowable) {
    super(pString, pThrowable);
    logThis();
  }

  public ExRuntimeRoot(String pString) {
    super(pString);
    logThis();
  }

  public ExRuntimeRoot() {
    super();
    logThis();
  }
}
