package com.fivium.scriptrunner2.ex;

/**
 * Exception class for errors encountered during a file promotion.
 */
public class ExPromote 
extends ExRoot {
  
  public ExPromote(Throwable pThrowable) {
    super(pThrowable);
  }

  public ExPromote(String pString, Throwable pThrowable) {
    super(pString, pThrowable);
  }

  public ExPromote(String pString) {
    super(pString);
  }

  public ExPromote() {
    super();
  }
}
