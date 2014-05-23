package com.fivium.scriptrunner2.script.parser;


import com.fivium.scriptrunner2.script.parser.ScriptParser.EscapeDelimiter;


/**
 * A segment of a ParsedStatement which contains unescaped text, i.e. not in a comment or quotation marks. This will
 * probably be treated as a SQL statement component by the Oracle parser. This is the "default" segment type of a statement;
 * statements are assumed to be unescaped until an escape sequence is encountered.
 */
class UnescapedTextSegment
extends ScriptSegment {
  
  UnescapedTextSegment(int pStartIndex){
    super(pStartIndex);
  }

  ScriptSegment consumeBuffer(String pBuffer) {
    
    //Index of the closest escape character
    int lClosestEscapeIdx = Integer.MAX_VALUE;    
    EscapeDelimiter lClosestEscapedDelimiter = null;
    
    //Index of the closest statement delimiter
    int lClosestDelimiterIndex = Integer.MAX_VALUE;
    
    //Loop through the remaining buffer characters looking for the first escape delimiter or statement delimiter
    CHAR_LOOP:    
    for(int i = 0; i < pBuffer.length(); i++){      
      
      //Check the character (range) at the current index for an escape delimiter or statement delimiter
      ESC_LOOP:
      for(EscapeDelimiter lEscapeDelimiter : ScriptParser.EscapeDelimiter.values()){
        
        //If this is the last EscapeDelimiter in the list, do an extra search for a statement delimiter
        //The only reason to wait until the last EscapeDelimiter is to limit the amount of times this check is performed
        if(lEscapeDelimiter == lEscapeDelimiter.values()[lEscapeDelimiter.values().length - 1]){
          //Tests if the current character is a statement delimiter by checking that it is a "/" character on its own line
          if(ScriptParser.statementDelimiterCharSearch(pBuffer, i)) {
            //If this char is a statement delimiter, record its position and break out of the loop
            lClosestDelimiterIndex = i;
            break CHAR_LOOP;
          }
        }
        
        if(pBuffer.length() < i + lEscapeDelimiter.mStartSequence.length()){
          //If the remaining buffer isn't long enough to contain this escape delimiter, don't bother checking
          continue ESC_LOOP;
        }
        
        if(pBuffer.substring(i, i + lEscapeDelimiter.mStartSequence.length()).equals(lEscapeDelimiter.mStartSequence)){
          //The character or character sequence at this index matches an escape delimiter - record the position and break out of the loop
          lClosestEscapeIdx = i;
          lClosestEscapedDelimiter = lEscapeDelimiter;
          break CHAR_LOOP;
        }
      } 
    }
        
    //Establish the index in the string which this undelimited section goes up to - whatever is closer out of the nearest
    //escape sequence, the nearest statement delimiter or the end of the buffer (if there are no further delimiters)
    int lGoesUpTo = Math.min(Math.min(lClosestEscapeIdx, lClosestDelimiterIndex), pBuffer.length());    
    
    //Set the contents of this unescaped segment
    setContents(pBuffer.substring(0, lGoesUpTo));    
    
    //If there was an escape sequence or statement delimiter matched
    if(lClosestEscapeIdx == getContents().length() || lClosestDelimiterIndex == getContents().length()){
      
      if(lClosestEscapeIdx < lClosestDelimiterIndex){
        //An escape sequence was found, return an escaped segment to continue the read from the index of the escape sequence
        return new EscapedTextSegment(lClosestEscapedDelimiter, lGoesUpTo);
      }
      else {
        //A statement delimiter was found, return a delimiter segment to continue the read from the index of the delimiter
        return new StatementDelimiterSegment(lGoesUpTo);
      }
      
    }
    else {
      //Whole string depleted, nothing else to read
      return null;
    }
  }
  

  /**
   * This segment is redundant if its contents has 0-length.
   * @return True if this is an empty segment.
   */
  boolean isRedundant() {
    return getContents().length() == 0;
  }
}
  
  