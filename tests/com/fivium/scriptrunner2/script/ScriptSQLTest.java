package com.fivium.scriptrunner2.script;


import com.fivium.scriptrunner2.ex.ExParser;
import com.fivium.scriptrunner2.script.parser.ParsedStatement;
import com.fivium.scriptrunner2.script.parser.ScriptParser;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import org.junit.Test;


public class ScriptSQLTest {
  public ScriptSQLTest() {
    super();
  }
  
  private ScriptSQL mScriptSQL;
  private Map<String, Integer> mHashOccurrenceCounter = new HashMap<String, Integer>();
  
  private ParsedStatement getParsedStatement(String pSQL) 
  throws ExParser {
    List<ParsedStatement> lList = ScriptParser.parse(pSQL);
    return lList.get(0);
  }
  
  @Test
  public void testBindParsing1() 
  throws ExParser {
    mScriptSQL = new ScriptSQL(getParsedStatement("SELECT :bind FROM dual\n/"), true, mHashOccurrenceCounter, 0);
    
    assertEquals("1 bind should be found", 1, mScriptSQL.getBindList().size());
    assertEquals("First bind should have correct name", "bind", mScriptSQL.getBindList().get(0));
  }
  
  @Test
  public void testBindParsing2() 
  throws ExParser {
    mScriptSQL = new ScriptSQL(getParsedStatement("SELECT ':notbind' FROM dual\n/"), true, mHashOccurrenceCounter, 0);    
    assertEquals("0 binds should be found", 0, mScriptSQL.getBindList().size());
  }
  
  @Test
  public void testBindParsing3() 
  throws ExParser {
    
    String lStatement = 
      "SELECT :b1, -- :commentedout1\n" +
      "FROM dual\n" +
      "/*\n" +
      ":commentedout2 \n" +
      "and :commentedout3 \n" +
      "*/\n" +
      "WHERE dummy = :b2\n" +
      "/";
    
    mScriptSQL = new ScriptSQL(getParsedStatement(lStatement), true, mHashOccurrenceCounter, 0);    
    assertEquals("2 binds should be found", 2, mScriptSQL.getBindList().size());
    assertEquals("First bind should have correct name", "b1", mScriptSQL.getBindList().get(0));
    assertEquals("Second bind should have correct name", "b2", mScriptSQL.getBindList().get(1));
  }
  
  @Test
  public void testBindParsing_SingleColonsNotBinds() 
  throws ExParser {
    String lStatement = 
      "DECLARE\n" + 
      "  v1 NUMBER;\n" + 
      "BEGIN\n" + 
      "  v1 := :1;\n" + 
      "END;\n" +
      "/";
    
    mScriptSQL = new ScriptSQL(getParsedStatement(lStatement), true, mHashOccurrenceCounter, 0);
    assertEquals("1 bind should be found", 1, mScriptSQL.getBindList().size());
  }
  
  @Test
  public void testBindParsing_ValidBindNames() 
  throws ExParser {
    //Valid bind names
    mScriptSQL = new ScriptSQL(getParsedStatement("SELECT :bind, :BIND, :bind#, :bind_123, :bind$ FROM dual\n/"), true, mHashOccurrenceCounter, 0);
    assertEquals("5 binds should be found", 5, mScriptSQL.getBindList().size());
    
    //Invalid bind names
    mScriptSQL = new ScriptSQL(getParsedStatement("SELECT :_bind, :#bind, :%bind, :&bind, :{bind} FROM dual\n/"), true, mHashOccurrenceCounter, 0);
    assertEquals("0 binds should be found", 0, mScriptSQL.getBindList().size());
  }
}
