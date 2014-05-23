package com.fivium.scriptrunner2.ex;

/**
 * Exception class for any errors caused by text file parsing.
 */
public class ExParser 
extends ExRoot {
  
  public ExParser() {
    super();
  }

  public ExParser(String pString) {
    super(pString);
  }

  public ExParser(String pString, Throwable pThrowable) {
    super(pString, pThrowable);
  }

  public ExParser(Throwable pThrowable) {
    super(pThrowable);
  }
}
