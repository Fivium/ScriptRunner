package com.fivium.scriptrunner2.script;


import com.fivium.scriptrunner2.ex.ExParser;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;


public class ScriptExecutableParserTest {
  public ScriptExecutableParserTest() {
    super();
  }
  
  List<ScriptExecutable> mResult;
  
  @Test
  public void testSimpleParse_Connect() 
  throws ExParser {
    
    String lScript = 
      "CONNECT schema\n" +
      "/";
    
    mResult = ScriptExecutableParser.parseScriptExecutables(lScript, false);
    assertEquals("Result should have 1 executable", 1, mResult.size());
    assertTrue("Executable should be a CONNECT",  mResult.get(0) instanceof ScriptConnect);
    assertEquals("CONNECT should connect to correct schema", "schema", ((ScriptConnect) mResult.get(0)).getUserName());
    
    //Check semicolons
    lScript = 
      "CONNECT schema;\n" +
      "/";
        
    mResult = ScriptExecutableParser.parseScriptExecutables(lScript, false);
    assertEquals("Result should have 1 executable", 1, mResult.size());
    assertTrue("Executable should be a CONNECT",  mResult.get(0) instanceof ScriptConnect);
    assertEquals("CONNECT should connect to correct schema", "schema", ((ScriptConnect) mResult.get(0)).getUserName());
  }
  
  @Test
  public void testSimpleParse_Disconnect() 
  throws ExParser {
    
    String lScript = 
      "DISCONNECT\n" +
      "/";
    
    mResult = ScriptExecutableParser.parseScriptExecutables(lScript, false);
    assertEquals("Result should have 1 executable", 1, mResult.size());
    assertTrue("Executable should be a DISCONNECT",  mResult.get(0) instanceof ScriptDisconnect);    
  }
  
  @Test
  public void testSimpleParse_Commit() 
  throws ExParser {
    
    String lScript = 
      "COMMIT\n" +
      "/";
    
    mResult = ScriptExecutableParser.parseScriptExecutables(lScript, false);
    assertEquals("Result should have 1 executable", 1, mResult.size());
    assertTrue("Executable should be a COMMIT",  mResult.get(0) instanceof ScriptCommit);    
  }
  
  @Test
  public void testSimpleParse_Script() 
  throws ExParser {
    
    String lScript = 
      "BEGIN\n" +
      "  null;\n" +
      "END;\n" +
      "/";
    
    mResult = ScriptExecutableParser.parseScriptExecutables(lScript, false);
    assertEquals("Result should have 1 executable", 1, mResult.size());
    assertTrue("Executable should be a SQL statement",  mResult.get(0) instanceof ScriptSQL);    
    assertEquals("SQL statement should have expected contents", "BEGIN\n  null;\nEND;\n", ((ScriptSQL) mResult.get(0)).getParsedSQL());
  }
  
  @Test
  public void testSimpleParse_ScriptWithConnect() 
  throws ExParser {
    
    String lScript = 
      "CONNECT schema\n" +
      "BEGIN\n" +
      "  null;\n" +
      "END;\n" +
      "/";
    
    mResult = ScriptExecutableParser.parseScriptExecutables(lScript, false);
    assertEquals("Result should have 3 executables", 3, mResult.size());
    
    assertTrue("First executable should be a CONNECT",  mResult.get(0) instanceof ScriptConnect); 
    assertEquals("CONNECT should connect to correct schema", "schema", ((ScriptConnect) mResult.get(0)).getUserName());
    
    assertTrue("Second executable should be SQL",  mResult.get(1) instanceof ScriptSQL); 
    assertEquals("SQL statement should have expected contents", "\nBEGIN\n  null;\nEND;\n", ((ScriptSQL) mResult.get(1)).getParsedSQL());
    
    assertTrue("Third executable should be DISCONNECT",  mResult.get(2) instanceof ScriptDisconnect);     
  }
  
  @Test
  public void testMultipleStatementParse() 
  throws ExParser {
    
    String lScript = 
      "CONNECT schema2\n" +
      "/\n" +      
      "CONNECT schema;\n" + //note semicolon
      "BEGIN\n" +
      "  null;\n" +
      "END;\n" +
      "/\n" +
      "\n" +
      "INSERT STATEMENT;\n" +
      "/\n" +
      "\n" +
      "COMMIT;\n" +
      "/\n" +
      "disconnect;\n" + //note lowercase
      "/";
    
    mResult = ScriptExecutableParser.parseScriptExecutables(lScript, false);
    assertEquals("Result should have 7 executables", 7, mResult.size());
    
    assertTrue("First executable should be a CONNECT",  mResult.get(0) instanceof ScriptConnect); 
    assertEquals("CONNECT should connect to correct schema", "schema2", ((ScriptConnect) mResult.get(0)).getUserName());
    
    assertTrue("Second executable should be a CONNECT",  mResult.get(1) instanceof ScriptConnect); 
    assertEquals("CONNECT should connect to correct schema", "schema", ((ScriptConnect) mResult.get(1)).getUserName());
    
    assertTrue("Third executable should be SQL",  mResult.get(2) instanceof ScriptSQL); 
    assertEquals("SQL statement should have expected contents", "\n\nBEGIN\n  null;\nEND;\n", ((ScriptSQL) mResult.get(2)).getParsedSQL());
    
    assertTrue("Fourth executable should be DISCONNECT",  mResult.get(3) instanceof ScriptDisconnect);    
    
    assertTrue("Fifth executable should be SQL",  mResult.get(4) instanceof ScriptSQL); 
    assertEquals("SQL statement should have expected contents", "\n\nINSERT STATEMENT;\n", ((ScriptSQL) mResult.get(4)).getParsedSQL());
    
    assertTrue("Sixth executable should be a COMMIT",  mResult.get(5) instanceof ScriptCommit); 
    
    assertTrue("Seventh executable should be a DISCONNECT",  mResult.get(6) instanceof ScriptDisconnect); 
  }
  
}