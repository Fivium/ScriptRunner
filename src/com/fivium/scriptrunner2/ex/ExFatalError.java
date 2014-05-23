package com.fivium.scriptrunner2.ex;

/**
 * General exception class for fatal errors which are caused by user input but are not expected to be handled.
 */
public class ExFatalError 
extends ExRuntimeRoot {
  
  public ExFatalError(Throwable pThrowable) {
    super(pThrowable);
  }

  public ExFatalError(String pString, Throwable pThrowable) {
    super(pString, pThrowable);
  }

  public ExFatalError(String pString) {
    super(pString);
  }

  public ExFatalError() {
    super();
  }
}
