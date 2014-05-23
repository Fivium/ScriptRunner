package com.fivium.scriptrunner2.script.parser;

import java.util.regex.Matcher;

/**
 * A special segment which represents a statement delimiter, i.e. the "/" character on an unescaped line by itself.
 */
class StatementDelimiterSegment
extends ScriptSegment {
  
  StatementDelimiterSegment(int pStartIndex){
    super(pStartIndex);
  }
  
  ScriptSegment consumeBuffer(String pStringBuilder) {
    //Find the delimiter in the buffer
    Matcher lMatcher = ScriptParser.STATEMENT_DELIMETER_PATTERN.matcher(pStringBuilder);
    lMatcher.find();
    //Set the contents of this segment to be the delimiter plus its surroundings
    setContents(lMatcher.group());
    //Assume the next thing to read will be unescaped text, starting from the end of this segment
    return new UnescapedTextSegment(lMatcher.end());
  }

}
