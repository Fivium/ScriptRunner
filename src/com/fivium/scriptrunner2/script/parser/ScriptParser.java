package com.fivium.scriptrunner2.script.parser;


import com.fivium.scriptrunner2.Logger;
import com.fivium.scriptrunner2.ex.ExParser;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;


/**
 * A parser for splitting strings of one or more statements into {@link ParsedStatement}s, which are in turn composed of
 * indiviudal {@link ScriptSegment}s. This class contains no state and does not need to be instantiated.
 */
public class ScriptParser {
  
  /** The pattern which delimits statements, currently a "/" on its own line. If this changes the statementDelimiterCharSearch method must also change to reflect this.  */
  static final Pattern STATEMENT_DELIMETER_PATTERN = Pattern.compile("^[ \\t]*/[ \\t]*$", Pattern.MULTILINE);
  
  /**
   * Character sequences which delimit escaped SQL segments.
   */
  enum EscapeDelimiter {    
    //Note that order is important: q-quotes before single quotes (otherwise single quote would match first in a search for q-quote end sequence)
    QQUOTE_BRACE("q'{", "}'", true),
    QQUOTE_SQUARE("q'[", "]'", true),
    QQUOTE_BANG("q'!", "!'", true),
    QQUOTE_PAREN("q'(", ")'", true),
    QQUOTE_ANGLE("q'<", ">'", true),
    COMMENT_MULTILINE("/*", "*/", true),
    DOUBLE_QUOTE("\"", "\"", true),
    SINGLE_QUOTE("'", "'", true),
    COMMENT_SINGLELINE("--", "\n", false); //possible problem here, will not work for files with Mac-only (\r) line endings
    
    final String mStartSequence;
    final String mEndSequence;
    final boolean mRequiresEndDelimiter;
    
    private EscapeDelimiter(String pStartSequence, String pEndSequence, boolean pRequiresEndDelimiter){
      mStartSequence = pStartSequence;
      mEndSequence = pEndSequence;
      mRequiresEndDelimiter = pRequiresEndDelimiter;
    }
    
    public String toString(){
      return mStartSequence;
    }
  }
  
  /**
   * Tests a string buffer for a statement delimiter at the given index. If the character at the index is the delimiter
   * character, the contents of the line the character is on is determined. If the line contents represents a statement delimiter,
   * the method returns true.<br/><br/>
   * A statement delimiter is considered to be the "/" character on a line by itself, disregarding whitespace.<br/><br/>
   * This method acts as a replacement for running the expensive STATEMENT_DELIMETER_PATTERN regular expresison when
   * parsing a script - this was causing performance issues.
   * @param pBuffer String to test.
   * @param pAtIndex Index of character to test.
   * @return True if this character is a new
   */
  static boolean statementDelimiterCharSearch(String pBuffer, int pAtIndex){
    
    //Don't bother searching if we're not starting from a delimiter character
    if(pBuffer.charAt(pAtIndex) != '/'){
      return false;
    }
    
    //Find the last newline before the current position
    int lLastNewlineBefore = pBuffer.substring(0, pAtIndex).lastIndexOf('\n');
    //Find the first newline after the current position
    int lFirstNewlineAfter = pBuffer.indexOf('\n', pAtIndex);
    
    //If we're at the start of the string
    if(lLastNewlineBefore == -1){
      lLastNewlineBefore = 0;
    }
    
    //If we're at the end of the string
    if(lFirstNewlineAfter == -1){
      lFirstNewlineAfter = pBuffer.length();
    }
    
    //The line contents is anything between the two newlines
    String lLine = pBuffer.substring(lLastNewlineBefore, lFirstNewlineAfter);
    
    //Trim the line and test for the delimiter
    return "/".equals(lLine.trim());    
  }
  
  private ScriptParser() {}
  
  /**
   * Splits a single string containing one or more delimited SQL scripts into a list of individual statements, represented
   * as {@link ParsedStatement}s. The splitter performs a basic parse of the string to account for the following Oracle SQL 
   * escape sequences:
   * <ul>
   * <li>Single quote (string literal)</li>
   * <li>Double quote (identifier)</li>
   * <li>Q-quoted string (e.g. <tt>q'{What's up}'</tt>)</li>
   * <li>Single line comment (--)</li>
   * <li>Multi line comment</li>
   * </ul>
   * Script delimeters within escape sequences are not used to split the script. The delimiter used is a single forward slash
   * on an otherwise empty line. This mirrors the Oracle SQL*Plus client syntax.
   * @param pScript Script to split.   
   * @return List of nested statements.
   * @throws ExParser If an escape sequence isn't terminated or if EOF is reached and unterminated input remains.
   */
  public static List<ParsedStatement> parse(String pScript)
  throws ExParser {
    
    String lRemainingScript = pScript;
    List<ParsedStatement> lStatementList = new ArrayList<ParsedStatement>();
    
    List<ScriptSegment> lCurrentStatementSegments = new ArrayList<ScriptSegment>();
    
    long lTimerOverallStart = System.currentTimeMillis();
    int lStatementCount = 0;
    Logger.logDebug("Parsing script");
    
    //Start with assuming that the first token to be encountered will be unescaped text
    ScriptSegment lCurrentScriptSegment = new UnescapedTextSegment(0);
    //Loop through the whole string, using individual segment objects to gradually deplete it. Segments deplete the buffer 
    //until they reach a terminating character (i.e. the start or end of an escape sequence, depending on the segment type)   
    long lTimerStatementStart = System.currentTimeMillis();
    do {      
      ScriptSegment lNextScriptPart = lCurrentScriptSegment.consumeBuffer(lRemainingScript);      
      
      //If the previous segment managed to read something, add its contents to the current statement
      if(!lCurrentScriptSegment.isRedundant()){
        lCurrentStatementSegments.add(lCurrentScriptSegment);
      }
      
      //We hit a delimiter, add the accumulated statement to the list
      if(lCurrentScriptSegment instanceof StatementDelimiterSegment){
        lStatementList.add(new ParsedStatement(lCurrentStatementSegments));
        lCurrentStatementSegments = new ArrayList<ScriptSegment>();        
        Logger.logDebug("Parsed statement " +  ++lStatementCount + " in " + (System.currentTimeMillis() - lTimerStatementStart + " ms"));        
        lTimerStatementStart = System.currentTimeMillis();
      }
      
      lCurrentScriptSegment = lNextScriptPart;
      if(lNextScriptPart != null){
        lRemainingScript = lRemainingScript.substring(lNextScriptPart.getStartIndex());
      }
    }
    while(lRemainingScript.length() > 0 && lCurrentScriptSegment != null);
    
    Logger.logDebug("Script parse complete in " + (System.currentTimeMillis() - lTimerOverallStart) + " ms");
    
    //Check there is no content at the end of the file which has not been delimited
    if(lCurrentStatementSegments.size() > 0){
      
      //If there is, and trimming it reveals it to be all whitespace, or comments, this is OK - otherwise fail      
      boolean lRealContentRemains = false;
      StringBuilder lUndelimitedScript = new StringBuilder();
      for(ScriptSegment lRemainingSegment : lCurrentStatementSegments) {
      
        lRemainingSegment.serialiseTo(lUndelimitedScript);
        
        if(lRemainingSegment instanceof UnescapedTextSegment){
          if(lRemainingSegment.getContents().trim().length() > 0){
            lRealContentRemains = true;            
          }
        }
        else if(lRemainingSegment instanceof EscapedTextSegment){
          if(!((EscapedTextSegment) lRemainingSegment).isComment()) {
            lRealContentRemains = true;
          }
        }
        
      }
      
      if(lRealContentRemains) {
        throw new ExParser("Undelimited input still in buffer at end of file:\n" + lUndelimitedScript.toString());
      }
      else {
        Logger.logDebug("Script has trailing comments/whitespace at end of file which is being ignored");
      }
    }
    
    return lStatementList;    
  }
}
