package com.fivium.scriptrunner2.ex;

/**
 * Root exception class for checked exceptions.
 */
public class ExRoot 
extends Exception {
  
  private void logThis(){
//    Logger.logInfo("ERROR:::");
//    Logger.logError(this);
  }
  
  public ExRoot(Throwable pThrowable) {    
    super(pThrowable);
    logThis();
  }

  public ExRoot(String pString, Throwable pThrowable) {
    super(pString, pThrowable);
    logThis();
  }

  public ExRoot(String pString) {
    super(pString);
    logThis();
  }

  public ExRoot() {
    super();
    logThis();
  }
}
