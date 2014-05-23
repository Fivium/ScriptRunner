package com.fivium.scriptrunner2.script.parser;

import com.fivium.scriptrunner2.ex.ExInternal;
import com.fivium.scriptrunner2.ex.ExParser;
import com.fivium.scriptrunner2.script.parser.ScriptParser.EscapeDelimiter;

/**
 * Segment of a SQL statement which is "escaped" in some way, i.e. part of a comment block or within quotation marks.
 */
class EscapedTextSegment
extends ScriptSegment {
  
  /** The delimiter string which is encapsulating this escaped text */
  EscapeDelimiter mEscapeDelimiter;
  
  EscapedTextSegment(EscapeDelimiter pEscapeDelimiter, int pStartIndex){
    super(pStartIndex);
    mEscapeDelimiter = pEscapeDelimiter;
  }
  
  ScriptSegment consumeBuffer(String pBuffer) 
  throws ExParser {
    
    //Sanity check that the buffer starts with the correct sequence
    if(pBuffer.indexOf(mEscapeDelimiter.mStartSequence) != 0){
      //This is an internal error, this method should not have been called if the buffer is not in the correct state
      throw new ExInternal("Sequence " + mEscapeDelimiter + " should start at position 0");
    }
    
    //Find the corresponding terminating sequence
    int lEndPosition = pBuffer.indexOf(mEscapeDelimiter.mEndSequence, 1);
    int lEndEscSeqLength = mEscapeDelimiter.mEndSequence.length();
    
    //If the remaining string doesn't contain this segment's termination sequence, that is a problem
    if(lEndPosition == -1){
      if(mEscapeDelimiter.mRequiresEndDelimiter){
        throw new ExParser("Unterminated escape sequence " + mEscapeDelimiter); 
      }
      else {
        //For "--" sequence, end of file counts as a terminator
        lEndPosition = pBuffer.length()-1;
      }
    }
    
    //Record the contents of this segment
    setContents(pBuffer.substring(mEscapeDelimiter.mStartSequence.length(), lEndPosition));
    
    //Return a new unescaped segment which will start reading from the end of this segment
    return new UnescapedTextSegment(lEndPosition + lEndEscSeqLength); 
  }

  void serialiseTo(StringBuilder pBuilder) {
    pBuilder.append(mEscapeDelimiter.mStartSequence);
    pBuilder.append(getContents());
    pBuilder.append(mEscapeDelimiter.mEndSequence);
  }

  /**
   * Gets the delimiter string which is encapsulating this escaped text.
   * @return Escape delimiter for this text.
   */
  EscapeDelimiter getEscapeDelimiter() {
    return mEscapeDelimiter;
  }
  
  /**
   * Tests if this segment is a single or multi line comment.
   * @return True if this segment represents a SQL comment.
   */
  public boolean isComment(){
    return mEscapeDelimiter == EscapeDelimiter.COMMENT_MULTILINE || mEscapeDelimiter == EscapeDelimiter.COMMENT_SINGLELINE;
  }

}
