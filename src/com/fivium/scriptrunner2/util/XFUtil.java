package com.fivium.scriptrunner2.util;

/**
 * General utility class.
 */
public class XFUtil {
  private XFUtil() {}
  
  /**
   * Tests if a string is null or empty.
   * @param pArg1
   * @return True if null or empty.
   */
  public static boolean isNull(String pArg1){
    return "".equals(pArg1 == null ? "" : pArg1);
  }
  
  /**
   * Returns arg2 if arg1 is null or empty, or arg1 otherwise.
   * @param pArg1
   * @param pArg2
   * @return
   */
  public static String nvl(String pArg1, String pArg2){
    return !isNull(pArg1) ? pArg1 : pArg2;
  }  
  
  /**
   * Returns arg2 if arg1 is null, or arg1 otherwise.
   * @param <T>
   * @param pArg1
   * @param pArg2
   * @return
   */
  public static <T> T nvl(T pArg1, T pArg2){
    return pArg1 != null ? pArg1 : pArg2;
  }
}
