package com.fivium.scriptrunner2.util;

import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;

/**
 * Convenience methods for hashing.
 */
public class HashUtil {
  private HashUtil() {}  
  
  private static final HashFunction gHashFunction = Hashing.md5();
  
  public static String hashString(String pString){
    //Rip out Windows carriage returns and trailing whitespace before hashing
    String lNormalisedString = pString.replaceAll("\\r", "").trim();
    
    HashCode lHashCode = gHashFunction.newHasher().putString(lNormalisedString).hash();
    return lHashCode.toString();
  }
}
