package com.fivium.scriptrunner2;


import com.fivium.scriptrunner2.ex.ExManifest;
import com.fivium.scriptrunner2.ex.ExParser;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;


public class ManifestParserTest {
  
  public ManifestParserTest() {
  }
  
  private Map<String, String> mPropertyMap;
  private ManifestParser mManifestParser;
  
  private static final String SINGLE_PROPERTY_MAP = "{p1 = \"v1\"}";
  private static final String SINGLE_PROPERTY_MAP_NO_QUOTES = "{p1 = v1}";  
  private static final String MULTI_PROPERTY_MAP = "{p1 = \"v1\", p2 =\t \"v2\", p3=  \"v3\"}";
  private static final String ESCAPED_PROPERTY_MAP = "{p1 = \"a=b\", p2=\"b,c,d\", p3=\"e,f=g\"}";
  
  private static final String MANIFEST_TEST1_PATH = "testfiles/manifest_test1.txt";

  @Before
  public void setUp()  {
  }

  @Test
  public void testParsePropertyMapString_Basic() 
  throws ExParser {
    
    mPropertyMap = ManifestParser.parsePropertyMapString(SINGLE_PROPERTY_MAP);
    
    assertEquals("Single property map should contain exactly 1 property", 1, mPropertyMap.size());
    assertEquals("Single property map should contain entry for p1 property", "v1", mPropertyMap.get("p1"));
  }
  
  @Test
  public void testParsePropertyMapString_NoQuotes() 
  throws ExParser {
    
    mPropertyMap = ManifestParser.parsePropertyMapString(SINGLE_PROPERTY_MAP_NO_QUOTES);
    
    assertEquals("Single property map should contain exactly 1 property", 1, mPropertyMap.size());
    assertEquals("Single property map should contain entry for p1 property", "v1", mPropertyMap.get("p1"));    
  }

  @Test
  public void testParsePropertyMapString_MultiProperty() 
  throws ExParser {
    
    mPropertyMap = ManifestParser.parsePropertyMapString(MULTI_PROPERTY_MAP);
    
    assertEquals("Multi property map should contain exactly 3 properties", 3, mPropertyMap.size());
    assertEquals("Multi property map should contain entry for p1 property", "v1", mPropertyMap.get("p1"));
    assertEquals("Multi property map should contain entry for p2 property", "v2", mPropertyMap.get("p2"));
    assertEquals("Multi property map should contain entry for p3 property", "v3", mPropertyMap.get("p3"));
  }
  
  @Test
  public void testParsePropertyMapString_EscapedProperties() 
  throws ExParser {
    
    mPropertyMap = ManifestParser.parsePropertyMapString(ESCAPED_PROPERTY_MAP);
    
    assertEquals("Multi property map should contain exactly 3 properties", 3, mPropertyMap.size());
    assertEquals("Equals sign should be preserved in property value", "a=b", mPropertyMap.get("p1"));
    assertEquals("Comma should be preserved in property value", "b,c,d", mPropertyMap.get("p2"));
    assertEquals("Comma and equals should be preserved in property value", "e,f=g", mPropertyMap.get("p3"));
  }
  
  @Test(expected = ExParser.class)
  public void testParsePropertyMapString_FailsWhenQuoteNotTerminated() 
  throws ExParser {
    mPropertyMap = ManifestParser.parsePropertyMapString("{p1 = \"unescaped}");
  }

  @Test(expected = ExParser.class)
  public void testParsePropertyMapString_FailsWhenBraceNotFirstCharacter() 
  throws ExParser {
    //Property string should start with { and end with }
    mPropertyMap = ManifestParser.parsePropertyMapString("p1=v1");
  }
  
  @Test(expected = ExParser.class)
  public void testParsePropertyMapString_FailsWhenBraceNotTerminated() 
  throws ExParser {
    mPropertyMap = ManifestParser.parsePropertyMapString("{p1=v1");
  }
  
  @Test(expected = ExParser.class)
  public void testParsePropertyMapString_FailsWhenDuplicatePropertiesSpecified() 
  throws ExParser {
    mPropertyMap = ManifestParser.parsePropertyMapString("{p1=v1, p1=v2}");
  }
  
  @Test(expected = ExParser.class)
  public void testParsePropertyMapString_InvalidEntryAfterComma() 
  throws ExParser {
    mPropertyMap = ManifestParser.parsePropertyMapString("{p1=v1,  }");
  }
  
  @Test
  public void testManifestParser_SimpleManifest() 
  throws ExParser, FileNotFoundException, IOException, ExManifest {
    mManifestParser = new ManifestParser(new File(this.getClass().getResource(MANIFEST_TEST1_PATH).getPath()));
    mManifestParser.parse();
    
    assertEquals("Promotion property map should contain promotion_label property", "manifest_test", mManifestParser.getPromotionPropertyMap().get("promotion_label"));
    assertEquals("Promotion property map should contain version property", "test", mManifestParser.getPromotionPropertyMap().get("scriptrunner_version"));
    
    assertEquals("Manifest file should contain 6 entries", 6, mManifestParser.getManifestEntryList().size());
    
    ManifestEntry lEntry = mManifestParser.getManifestEntryList().get(0);
    assertNotNull("Manifest should contain an entry at index 0", lEntry);
    
    assertEquals("Entry 0 should have sequence 1000", 1000, lEntry.getSequencePosition());
    assertEquals("Entry 0 should have correct path", "DatabasePatches/CorePatches/PATCHCORE00001 (test 1).sql", lEntry.getFilePath());
    assertEquals("Entry 0 should have 'Patch' loader", "Patch", lEntry.getLoaderName());
    assertEquals("Entry 0 should have property map with correct value", "v1", lEntry.getPropertyMap().get("p1"));
    
    assertFalse("Entry 0 should not be an augmentation entry", lEntry.isAugmentation());
    assertFalse("Entry 0 should not be a forced duplicate entry", lEntry.isForcedDuplicate());
    
    lEntry = mManifestParser.getManifestEntryList().get(1);
    assertNotNull("Manifest should contain an entry at index 1", lEntry);
    
    assertEquals("Entry 1 should have sequence 2000", 2000, lEntry.getSequencePosition());
    assertEquals("Entry 1 should have correct path, with leading slash trimmed", "DatabaseSource/CoreSource/PACKAGE.pkb", lEntry.getFilePath());
    assertEquals("Entry 1 should have 'Source' loader", "Source", lEntry.getLoaderName());
    assertEquals("Entry 1 should have property map with correct value", "v2", lEntry.getPropertyMap().get("p2"));
        
    lEntry = mManifestParser.getManifestEntryList().get(2);
    assertNotNull("Manifest should contain an entry at index 2", lEntry);
    
    assertEquals("Entry 2 should have correct path, with slashes normalised to forward slashes", "DatabasePatches/CorePatches/PATCHCORE00002 (test 2).sql", lEntry.getFilePath());
    assertEquals("Entry 2 should have empty property map", 0, lEntry.getPropertyMap().size());
    
    lEntry = mManifestParser.getManifestEntryList().get(3);
    assertNotNull("Manifest should contain an entry at index 3", lEntry);
    
    assertEquals("Entry 3 should have correct path", "DatabaseSource/CoreSource/PACKAGE.pkb", lEntry.getFilePath());
    
    assertTrue("Entry 3 should be an augmentation entry", lEntry.isAugmentation()); 
    assertFalse("Entry 3 should not be a forced duplicate entry", lEntry.isForcedDuplicate());
    assertEquals("Entry 3 should have 'Different_Source' loader", "Different_Source", lEntry.getLoaderName());
    assertEquals("Entry 3 should have property map with correct value", "v3", lEntry.getPropertyMap().get("p3"));
    
    lEntry = mManifestParser.getManifestEntryList().get(4);
    assertNotNull("Manifest should contain an entry at index 4", lEntry);
    
    assertEquals("Entry 4 should have correct path", "DatabaseSource/CoreSource/PACKAGE.pkb", lEntry.getFilePath());
    
    assertFalse("Entry 4 should not be an augmentation entry", lEntry.isAugmentation()); 
    assertTrue("Entry 4 should be a forced duplicate entry", lEntry.isForcedDuplicate());
    assertEquals("Entry 4 should have sequence 2050", 2050, lEntry.getSequencePosition());
    assertEquals("Entry 4 should have property map with correct value", "v4", lEntry.getPropertyMap().get("p4"));
    
    lEntry = mManifestParser.getManifestEntryList().get(5);
    assertNotNull("Manifest should contain an entry at index 5", lEntry);
    
    assertEquals("Entry 5 should have correct path", "DatabaseSource/CoreSource/File's dodgy#name {here+%}.txt", lEntry.getFilePath());
    
    assertFalse("Entry 5 should not be an augmentation entry", lEntry.isAugmentation()); 
    assertFalse("Entry 5 should be a forced duplicate entry", lEntry.isForcedDuplicate());
    assertEquals("Entry 5 should have sequence 5000", 5000, lEntry.getSequencePosition());
    assertEquals("Entry 5 should have property map with correct value", "v1", lEntry.getPropertyMap().get("p1"));
    
  }
  
}
