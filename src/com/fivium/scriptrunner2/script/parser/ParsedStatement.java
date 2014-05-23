package com.fivium.scriptrunner2.script.parser;


import com.fivium.scriptrunner2.ex.ExInternal;
import com.fivium.scriptrunner2.script.parser.ScriptParser.EscapeDelimiter;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An individual SQL statement which has been parsed into a list of escaped and unesecaped {@link ScriptSegment}s. This
 * is not guaranteed to be syntactically valid SQL, and requires serialising into a string using the {@link #getStatementString()}
 * method before it can be executed.<br/><br/>
 *
 * ParsedStatements are mutable as the contents of their encapsulated segments is subject to change.
 */
public class ParsedStatement {
  
  private final List<ScriptSegment> mSegmentList;
  
  /**
   * Constructs a new ParsedStatement from the segments provided in the list. The list may contain a 
   * {@link StatementDelimiterSegment}, but if it does this must be the last segment in the list.
   * @param pSegmentList List of segments which will comprise the new ParsedStatement.
   */
  ParsedStatement(List<ScriptSegment> pSegmentList){
    mSegmentList = pSegmentList;
    
    //Check statement delimiter is only at the end of the list, if at all
    int i = 0;
    for(ScriptSegment lSegment : mSegmentList){
      if(lSegment instanceof StatementDelimiterSegment && i < pSegmentList.size()-1){
        throw new ExInternal("Delimiter segment must be the last segment in a ParsedStatement " + i + " " + pSegmentList.size());
      }
      i++;
    }
  }
  
  /**
   * Gets the full string representation of this parsed statement, not including its terminating delimiter.
   * @return The full statement string.
   */
  public String getStatementString(){  
    return getStatementString(false);
  }
  
  /**
   * Gets the full string representation of this parsed statement.
   * @param pIncludeDelimiter If true, the original delimiter (e.g. "/" character) is included in the serialisation.
   * @return The full statement string.
   */
  public String getStatementString(boolean pIncludeDelimiter){  
    
    StringBuilder lBuilder = new StringBuilder();
    for(ScriptSegment lSegment : mSegmentList){
      if(!(lSegment instanceof StatementDelimiterSegment) || pIncludeDelimiter){
        lSegment.serialiseTo(lBuilder);
      }      
    }
    
    return lBuilder.toString();
  }
  
  /**
   * Internal test to check if this statment consists entirely of either empty content or escaped content which is escaped
   * by a delimiter in pAllowedEscapeDelimiters.
   * @param pAllowedEscapeDelimiters Escape characters to test for.
   * @return True if this statement consists entirely of escaped content or empty content.
   */
  private boolean isAllEscapedOrEmpty(EnumSet<EscapeDelimiter> pAllowedEscapeDelimiters){
    
    boolean lIsEscaped = true;
    for(ScriptSegment lSegment : mSegmentList){
      
      if(lSegment instanceof StatementDelimiterSegment) {
        //Ignore these as they are not part of the statement itself
        continue;
      }
      else if (lSegment instanceof UnescapedTextSegment){
        //If this is unescaped text which is not all whitespace, it's not all escaped
        if(lSegment.getContents().trim().length() > 0){
          lIsEscaped = false;
          break;
        }
      }
      else if (lSegment instanceof EscapedTextSegment){
        //Check that the escape sequence counts for what we want - if not, it's not all escaped
        if(!pAllowedEscapeDelimiters.contains(((EscapedTextSegment) lSegment).getEscapeDelimiter())){
          lIsEscaped = false;
          break;
        }
      }      
    }
    
    return lIsEscaped;
  }
  
  /**
   * Tests if this statement consists entirely of single or multi line comment blocks, or empty non-escaped content.
   * @return True if the statement is all comments or empty content.
   */
  public boolean isAllCommentsOrEmpty(){
    return isAllEscapedOrEmpty(EnumSet.of(EscapeDelimiter.COMMENT_MULTILINE, EscapeDelimiter.COMMENT_SINGLELINE));
  }
  
  /**
   * Tests if this statement consists entirely of any form of escaped content, or empty non-escaped content.
   * @return True if the statement is all escaped content or empty content.
   */
  public boolean isAllEscapedOrEmpty(){
    return isAllEscapedOrEmpty(EnumSet.allOf(EscapeDelimiter.class));
  }
  
  /**
   * Replaces the given pattern within unescaped segments of this statement with the given replacement string, n times.   
   * @param pPattern Pattern to match for replacement.
   * @param pReplacement String to replace matches with.
   * @param pReplaceLimit Number of times to perform the replacement.
   * @return A list of the strings which were replaced. This will be a 0-length list if nothing was replaced.
   */
  public List<String> replaceInUnescapedSegments(Pattern pPattern, String pReplacement, int pReplaceLimit){
    return replaceInUnescapedSegmentsInternal(pPattern, pReplacement, pReplaceLimit);
  }
  
  /**
   * Replaces all occurrences of the given pattern within unescaped segments of this statement with the given replacement string.
   * @param pPattern Pattern to match for replacement.
   * @param pReplacement String to replace matches with.
   * @return A list of the strings which were replaced. This will be a 0-length list if nothing was replaced.
   */
  public List<String> replaceInUnescapedSegments(Pattern pPattern, String pReplacement){
    return replaceInUnescapedSegmentsInternal(pPattern, pReplacement, -1);
  }
  
  private List<String> replaceInUnescapedSegmentsInternal(Pattern pPattern, String pReplacement, int pReplaceLimit){
    
    List<String> lReplacedStringsList = new  ArrayList<String>();
    int lReplaceCount = 0;
    
    for(ScriptSegment lSegment : mSegmentList){
      if(lSegment instanceof UnescapedTextSegment) {
        
        //Match on the contents of this unescaped segment
        Matcher lMatcher = pPattern.matcher(lSegment.getContents());
        
        StringBuffer lNewContents = new StringBuffer();
        while (lMatcher.find() && (pReplaceLimit == -1 || lReplaceCount < pReplaceLimit)) {
          //Make a record of the string we replaced
          lReplacedStringsList.add(lMatcher.group());
          lMatcher.appendReplacement(lNewContents, pReplacement);
          lReplaceCount++;
        }
        lMatcher.appendTail(lNewContents);
        
        //Replace the contents of the segment with the processed string
        lSegment.setContents(lNewContents.toString());
        
      }
    }
    
    return lReplacedStringsList;    
  }
  
}