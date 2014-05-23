package com.fivium.scriptrunner2.script.parser;


import com.fivium.scriptrunner2.ex.ExParser;

import java.util.List;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;


public class ScriptParserTest {
  public ScriptParserTest() {
    super();
  }
  
  List<ParsedStatement> mResult;
  
  @Test
  public void testSimpleParse() 
  throws ExParser {
    
    String lParseString = 
      "STATEMENT1 LINE1\n" +
      "STATEMENT1 LINE2\n" +
      "/\n" +
      "STATEMENT2 LINE1\n" +
      "STATEMENT2 LINE2\n" +
      "/";

    mResult = ScriptParser.parse(lParseString);

    assertEquals("Result has 2 statements", 2, mResult.size());
    
    assertEquals("First statement should have expected contents", "STATEMENT1 LINE1\nSTATEMENT1 LINE2\n", mResult.get(0).getStatementString());
    assertEquals("Second statement should have expected contents", "\nSTATEMENT2 LINE1\nSTATEMENT2 LINE2\n", mResult.get(1).getStatementString());
  }
  
  @Test
  public void testParseWithEscapedContent1() 
  throws ExParser {
    
    String lParseString = 
      "SELECT 'hello' \"prompt\"\n" +
      "FROM dual\n" +
      "/";
    
    mResult = ScriptParser.parse(lParseString);

    assertEquals("Result has 1 statement", 1, mResult.size());
    assertEquals("Result has expected contents", "SELECT 'hello' \"prompt\"\nFROM dual\n", mResult.get(0).getStatementString());
  }
  
  @Test
  public void testParseWithEscapedContent2() 
  throws ExParser {
    
    String lParseString = 
      "SELECT q'{hello world's end}', q\"{q \"string\"}\" \n" +
      "FROM dual\n" +
      "/";
    
    mResult = ScriptParser.parse(lParseString);

    assertEquals("Result has 1 statement", 1, mResult.size());
    assertEquals("Result has expected contents", "SELECT q'{hello world's end}', q\"{q \"string\"}\" \nFROM dual\n", mResult.get(0).getStatementString());
  }
  
  @Test
  public void testParseWithEscapedContent3() 
  throws ExParser {
    
    String lParseString = 
      "SELECT dummy --comment\n" +
      "FROM dual\n" +
      "/* comment */\n" +
      "/";
    
    mResult = ScriptParser.parse(lParseString);

    assertEquals("Result has 1 statement", 1, mResult.size());
    assertEquals("Result has expected contents", "SELECT dummy --comment\nFROM dual\n/* comment */\n", mResult.get(0).getStatementString());
  }
  
  @Test
  public void testCommentDetection_SingleLine() 
  throws ExParser {
    
    String lParseString = 
      "SELECT dummy\n" +
      "FROM dual\n" +
      "/\n" +
      "--SELECT dummy2\n" +
      "--FROM dual2\n" +
      "/\n" +
      "SELECT dummy3\n" +
      "FROM dual3\n" +
      "/";
    
    mResult = ScriptParser.parse(lParseString);

    assertEquals("Result has 3 statements", 3, mResult.size());
    
    assertEquals("First statement should have expected contents",  "SELECT dummy\nFROM dual\n", mResult.get(0).getStatementString());
    assertEquals("Second statement should have expected contents",  "\n--SELECT dummy2\n--FROM dual2\n", mResult.get(1).getStatementString());
    assertEquals("Third statement should have expected contents",  "\nSELECT dummy3\nFROM dual3\n", mResult.get(2).getStatementString());
    
    assertTrue("Second statement should be reported as a comment", mResult.get(1).isAllCommentsOrEmpty());
  }
  
  @Test
  public void testCommentDetection_MultiLine() 
  throws ExParser {
    
    String lParseString = 
      "SELECT dummy\n" +
      "FROM dual\n" +
      "/\n" +
      "/*SELECT dummy2\n" +
      "FROM dual2*/\n" +
      "/\n" +
      "SELECT dummy3\n" +
      "FROM dual3\n" +
      "/";
    
    mResult = ScriptParser.parse(lParseString);

    assertEquals("Result has 3 statements", 3, mResult.size());
    
    assertEquals("First statement should have expected contents",  "SELECT dummy\nFROM dual\n", mResult.get(0).getStatementString());
    assertEquals("Second statement should have expected contents",  "\n/*SELECT dummy2\nFROM dual2*/\n", mResult.get(1).getStatementString());
    assertEquals("Third statement should have expected contents",  "\nSELECT dummy3\nFROM dual3\n", mResult.get(2).getStatementString());
    
    assertTrue("Second statement should be reported as a comment", mResult.get(1).isAllCommentsOrEmpty());
  }
  
  @Test
  public void testParseWithEscapedStatementDelimiter1() 
  throws ExParser {
    
    String lParseString = 
      "SELECT dummy\n" +
      "FROM dual\n" +
      "/\n" +
      "/* comment \n" +
      "/\n" +
      "end comment */\n" +
      "/\n" +
      "SELECT dummy2\n" +
      "FROM dual2\n" +
      "/\n";
    
    mResult = ScriptParser.parse(lParseString);

    assertEquals("Result has 3 statements", 3, mResult.size());
    
    assertEquals("First statement should have expected contents",  "SELECT dummy\nFROM dual\n", mResult.get(0).getStatementString());    
    assertEquals("Third statement should have expected contents",  "\nSELECT dummy2\nFROM dual2\n", mResult.get(2).getStatementString());
    
    assertTrue("Second statement should be reported as a comment", mResult.get(1).isAllCommentsOrEmpty());    
  }
  
  @Test
  public void testParseWithEscapedStatementDelimiter2() 
  throws ExParser {
    
    String lParseString = 
      "SELECT dummy\n" +
      "FROM dual\n" +
      "/\n" +
      "'\n" +
      "/\n" +
      "'\n" +
      "/\n" +
      "SELECT dummy2\n" +
      "FROM dual2\n" +
      "/\n";
    
    mResult = ScriptParser.parse(lParseString);

    assertEquals("Result has 3 statements", 3, mResult.size());
    
    assertEquals("First statement should have expected contents",  "SELECT dummy\nFROM dual\n", mResult.get(0).getStatementString());    
    assertEquals("Second statement should have expected contents",  "\n'\n/\n'\n", mResult.get(1).getStatementString());    
    assertEquals("Third statement should have expected contents",  "\nSELECT dummy2\nFROM dual2\n", mResult.get(2).getStatementString());
    
    assertTrue("Second statement should be reported as escaped", mResult.get(1).isAllEscapedOrEmpty());
  }
  
  @Test
  public void testParseApostropheHandling() 
  throws ExParser {
    
    String lParseString = 
      "SELECT 'that''s ok'\n" +
      "FROM dual\n" +
      "/\n" +
      "''\n" +
      "/\n";
    
    mResult = ScriptParser.parse(lParseString);

    assertEquals("Result has 2 statements", 2, mResult.size());
    
    assertEquals("First statement should have expected contents",  "SELECT 'that''s ok'\nFROM dual\n", mResult.get(0).getStatementString());    
    assertEquals("Seond statement should have expected contents",  "\n''\n", mResult.get(1).getStatementString());
    
    assertTrue("Second statement should be reported as escaped", mResult.get(1).isAllEscapedOrEmpty());
  }
  
  @Test
  public void testParseMulitipleEscapeSequencesPerLine() 
  throws ExParser {
    
    String lParseString = 
      "SELECT '<assign initTarget=\":{temp}/XXX\" expr=\"substring-before(:{action}/UREF,''YYY'')\"></assign>' from dual\n" +
      "/\n" +
      "SELECT extract(xml_data, '/*/ELEMENT1/ELEMENT2/*[name(.)!=\"ELEMENT3\"]')\n" +     
      "/\n" + 
      "SELECT dummy -- Can't do this\n" +
      "FROM dual\n" +
      "/";
     
    mResult = ScriptParser.parse(lParseString);

    assertEquals("Result has 2 statements", 3, mResult.size());
    
    assertEquals("First statement should have expected contents", "SELECT '<assign initTarget=\":{temp}/XXX\" expr=\"substring-before(:{action}/UREF,''YYY'')\"></assign>' from dual\n", mResult.get(0).getStatementString());    
    assertEquals("Second statement should have expected contents", "\nSELECT extract(xml_data, '/*/ELEMENT1/ELEMENT2/*[name(.)!=\"ELEMENT3\"]')\n", mResult.get(1).getStatementString());
    assertEquals("Third statement should have expected contents", "\nSELECT dummy -- Can't do this\nFROM dual\n", mResult.get(2).getStatementString());
    
  }
  
  @Test
  public void testParseIgnoresCommentSegmentAfterFinalTerminator() 
  throws ExParser {    
    String lParseString = 
      "DELIMITED LINE1\n" +
      "/\n" +
      "/* UNDELIMITED LINE1\n */";
    mResult = ScriptParser.parse(lParseString);
    
    assertEquals("Result has 1 statements", 1, mResult.size());
    assertEquals("First statement should have expected contents", "DELIMITED LINE1\n", mResult.get(0).getStatementString());    
  }
  
  @Test
  public void testParseIgnoresWhitespaceAndCommentSegmentsAfterFinalTerminator() 
  throws ExParser {    
    String lParseString = 
      "DELIMITED LINE1\n" +
      "/\n" +
      "\n  /* comment */ \n\n  \t   \t /*comment*/";
    mResult = ScriptParser.parse(lParseString);
    
    assertEquals("Result has 1 statements", 1, mResult.size());
    assertEquals("First statement should have expected contents", "DELIMITED LINE1\n", mResult.get(0).getStatementString());    
  }
  
  
  @Test(expected = ExParser.class)
  public void testParseFailsWhenNoFinalDelimiter1() 
  throws ExParser {    
    String lParseString = "UNDELIMITED LINE1\n";
    mResult = ScriptParser.parse(lParseString);    
  }
  
  @Test(expected = ExParser.class)
  public void testParseFailsWhenNoFinalDelimiter2() 
  throws ExParser {    
    String lParseString = 
      "DELIMITED LINE1\n" +
      "/\n" +
      "UNDELIMITED LINE1\n";
    mResult = ScriptParser.parse(lParseString);    
  }
  
  
  @Test(expected = ExParser.class)
  public void testParseFailsWhenEscapeDelimiterNotTerminated1() 
  throws ExParser {    
    String lParseString = "hello 'world\n/";
    mResult = ScriptParser.parse(lParseString);    
  }
  
  @Test(expected = ExParser.class)
  public void testParseFailsWhenEscapeDelimiterNotTerminated2() 
  throws ExParser {    
    String lParseString = "hello /* world\n/";
    mResult = ScriptParser.parse(lParseString);    
  }
  
  @Test
  public void testParseWithSingleLineCommentAtEOF() 
  throws ExParser {
    
    String lParseString = 
      "--SELECT *\n" +
      "--FROM dual\n" +
      "--/";
    
    mResult = ScriptParser.parse(lParseString);

    assertEquals("Result has 0 statements", 0, mResult.size());
  }
  
  @Test
  public void testParseWithSingleLineCommentAtEndOfStatement() 
  throws ExParser {
    
    String lParseString = 
      "SELECT *\n" +
      "--FROM dual\n" +
      "/";
    
    mResult = ScriptParser.parse(lParseString);

    assertEquals("Result has 1 statements", 1, mResult.size());
    //Attempt to replace "dual" with "test" - this should not happen as the "dual" string is in a comment (i.e. escaped)
    mResult.get(0).replaceInUnescapedSegments(Pattern.compile("dual"), "test");
    
    assertEquals("Result should not treat final comment as unescaped segment",   "SELECT *\n--FROM dual\n", mResult.get(0).getStatementString());
  }
  
}
