package com.fivium.scriptrunner2.builder;


import com.fivium.scriptrunner2.ex.ExManifestBuilder;
import com.fivium.scriptrunner2.ex.ExParser;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;

import java.util.TreeMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import org.junit.Before;
import org.junit.Test;


public class ManifestBuilderTest {
  public ManifestBuilderTest() {
    super();
  }
  
  private ManifestBuilder mManifestBuilder;
  private TreeMap<Integer, BuilderManifestEntry> mEntryMap;
  
  private static final String LABEL_TEST = "test";
  
  @Before
  public void setUp() 
  throws ExManifestBuilder, ExParser {
    mManifestBuilder = new ManifestBuilder(new File(this.getClass().getResource("testfiles").getPath()), LABEL_TEST);    
    PrintWriter lWriter = new PrintWriter(new StringWriter());
//    PrintWriter lWriter = new PrintWriter(System.out);
    mManifestBuilder.buildManifest(new File(this.getClass().getResource("testfiles/ScriptRunner/additionalprops.txt").getPath()), lWriter);
    mEntryMap = mManifestBuilder.getManifestEntryMap();
  }
  
  @Test
  public void testManifestBuilder_EntryCount() 
  throws ExParser, ExManifestBuilder {
    assertEquals("Builder should resolve 12 entries (ignored entry should not be counted)", 12,  mEntryMap.entrySet().size());    
  }
  
  @Test
  public void testManifestBuilder_DefaultSequences() 
  throws ExParser, ExManifestBuilder {
    //Next should be files select by rule 1 (in SourceFolder) starting at 1010    
    assertEquals("Source1 should be at the next entry (position 1010)", (Integer)  1010, mEntryMap.higherKey(mEntryMap.firstKey()));    
    assertEquals("Source1 should be at position 1010", "SourceFolder/Source1.txt", mEntryMap.get(1010).getFilePath());
    
    assertEquals("File positions in SourceFolder should increment by 10", (Integer)  1020, mEntryMap.higherKey(1010));    
    assertEquals("Files should be globbed in alphabetical order",  "SourceFolder/Source2.txt", mEntryMap.get(1020).getFilePath());
    
    //Source3 is selected by this rule but its position is overwritten so the following file (Source4) should be next in sequence
    assertEquals("Source4.txt should appear directly below Source2.txt",  "SourceFolder/Source4.txt", mEntryMap.get(1030).getFilePath());
    
    //OtherSource0 should appear first as it is globbed first
    assertEquals("OtherSource4 should start at position 1501", (Integer)  1501, mEntryMap.higherKey(1030));
    assertEquals("OtherSource4 should be at position 1501", "SourceFolder2/OtherSource4.txt", mEntryMap.get(1501).getFilePath());
    
    //Following should be files in SourceFolder2 incrementing by 1 position each time
    assertEquals("Additional SourceFolder2 entries should start at position 2001", (Integer)  2001, mEntryMap.higherKey(1501));
    assertEquals("OtherSource2 should be at position 2001", "SourceFolder2/OtherSource2.txt", mEntryMap.get(2001).getFilePath());
    
    assertEquals("SourceFolder2 entries should increment position by 1 each time", (Integer)  2002, mEntryMap.higherKey(2001));   
    assertEquals("OtherSource3 should be at position 2002", "SourceFolder2/OtherSource3.txt", mEntryMap.get(2002).getFilePath());
  }
  
  @Test
  public void testManifestBuilder_StandardEntriesNotDuplicated() 
  throws ExParser, ExManifestBuilder {
    
    int lCount = 0;
    for(BuilderManifestEntry lEntry : mEntryMap.values()){
      if("SourceFolder/Source1.txt".equals(lEntry.getFilePath())){
        lCount++;
      }
    }
    
    assertEquals("Source1.txt should have exactly 1 entry", 1, lCount);
  }
  
  @Test
  public void testManifestBuilder_FileHashProperty() 
  throws ExParser, ExManifestBuilder {
    //Test all files have file_hash
    for(BuilderManifestEntry lEntry : mEntryMap.values()){
      assertNotNull("All files should have hash property", lEntry.getPropertyMap().get("file_hash"));      
    }
  }
  
  @Test
  public void testManifestBuilder_FileHashedCorrectly() 
  throws ExParser, ExManifestBuilder {
    assertEquals("Source1.txt should have expected hash", "5eb63bbbe01eeed093cb22bb8f5acdc3",  mEntryMap.get(1010).getPropertyMap().get("file_hash"));
  }
  
  @Test
  public void testManifestBuilder_DefaultProperties() 
  throws ExParser, ExManifestBuilder {
    //Test default properties come in from the config file
    assertEquals("OtherSource2.txt at position 50 should have default property", "a",  mEntryMap.get(50).getPropertyMap().get("standard_prop"));
    assertEquals("OtherSource2.txt at position 2001 should have default property", "a",  mEntryMap.get(2001).getPropertyMap().get("standard_prop"));
    assertEquals("OtherSource3.txt at position 2002 should have default property", "a",  mEntryMap.get(2002).getPropertyMap().get("standard_prop"));
  }
  
  @Test
  public void testManifestBuilder_PropertyAugmentation() 
  throws ExParser, ExManifestBuilder {
    
    //We have already asserted above that these files are Source1 and Source2
    assertEquals("Agumentation for Source1.txt should add new property", "1",  mEntryMap.get(1010).getPropertyMap().get("new_prop"));
    assertEquals("Agumentation for Source2.txt should add new property", "2",  mEntryMap.get(1020).getPropertyMap().get("new_prop"));
    
    //All 3 entries for OtherSource2 should have new property
    assertEquals("OtherSource2.txt at position 50 should have new property", "3",  mEntryMap.get(50).getPropertyMap().get("new_prop"));
    assertEquals("OtherSource2.txt at position 2001 should have new property", "3",  mEntryMap.get(2001).getPropertyMap().get("new_prop"));
    assertEquals("OtherSource2.txt at position 5000 should have new property (overloaded)", "4",  mEntryMap.get(5000).getPropertyMap().get("new_prop"));
    
    //This is overloaded by an augmentation entry
    assertEquals("OtherSource1.txt at position 4000 should have overloaded default property", "b",  mEntryMap.get(4000).getPropertyMap().get("standard_prop"));
    assertEquals("OtherSource1.txt at position 4000 should have additional new property", "x",  mEntryMap.get(4000).getPropertyMap().get("new_prop_2"));
    
    //This is overloaded by a forced duplicate entry
    assertEquals("OtherSource2.txt at position 5000 should have overloaded default property", "c",  mEntryMap.get(5000).getPropertyMap().get("standard_prop"));
  }
  
  @Test
  public void testManifestBuilder_PositionOverloading() 
  throws ExParser, ExManifestBuilder {
    
    //OtherSource2 should be at the start because its position is overriden
    assertEquals("OtherSource2.txt should be at position 50 due to position override", "SourceFolder2/OtherSource2.txt", mEntryMap.get(50).getFilePath());
    
    assertEquals("OtherSource1.txt should be at position 4000 due to position override", "SourceFolder2/OtherSource1.txt", mEntryMap.get(4000).getFilePath());
    assertEquals("OtherSource2.txt should be at position 5000 due to position override", "SourceFolder2/OtherSource2.txt", mEntryMap.get(5000).getFilePath());    
    
    assertEquals("OtherSource5.txt should be at position 6000 due to position override", "SourceFolder2/OtherSource5.txt", mEntryMap.get(6000).getFilePath());    
    assertEquals("OtherSource5.txt should be at position 6001 due to forced duplicate", "SourceFolder2/OtherSource5.txt", mEntryMap.get(6001).getFilePath());    
    assertNull("OtherSource5.txt should NOT be at position 2003 due to position override", mEntryMap.get(2003));
  }
  
  @Test
  public void testManifestBuilder_LoaderAugmentation() 
  throws ExParser, ExManifestBuilder {
    
    //We have already asserted above that these files are in their correct positions
    assertEquals("Source1.txt should have default loader despite property overloading", "Loader1",  mEntryMap.get(1010).getLoaderName());
    assertEquals("Source2.txt should have Loader2 due to augementation line overloading", "Loader2",  mEntryMap.get(1020).getLoaderName());
    assertEquals("Source4.txt should have default loader", "Loader1",  mEntryMap.get(1030).getLoaderName());
    
    //OtherSource2's default loader is Loader2 but it is augmented in some positions
    assertEquals("OtherSource2.txt at position 50 should have overloaded loader", "Loader3",  mEntryMap.get(50).getLoaderName());
    assertEquals("OtherSource2.txt in default position should have overloaded loader due to augmentation line", "Loader1",  mEntryMap.get(2001).getLoaderName());
    assertEquals("OtherSource2.txt at position 5000 should have standard loader", "Loader2",  mEntryMap.get(5000).getLoaderName());
  }
  
  @Test
  public void testManifestBuilder_AdditionalProperties() 
  throws ExParser, ExManifestBuilder {
    
    assertEquals("Source1.txt should have property from additional properties file", "abc",  mEntryMap.get(1010).getPropertyMap().get("add_prop"));
    assertEquals("Source2.txt should have property from additional properties file", "abc",  mEntryMap.get(1020).getPropertyMap().get("add_prop"));
    
    assertNull("Source4.txt should not have property from additional properties file", mEntryMap.get(1030).getPropertyMap().get("add_prop"));    
  }
  
  @Test
  public void testManifestBuilder_PromotionProperties() 
  throws ExParser, ExManifestBuilder {
    //Check promotion properties are set
    assertEquals("Promotion label property should be set", LABEL_TEST, mManifestBuilder.getPromotionPropertyMap().get("promotion_label"));
    assertNotNull("Generated datetime property should be set", mManifestBuilder.getPromotionPropertyMap().get("manifest_generated_datetime"));
    assertNotNull("ScriptRunner version property should be set", mManifestBuilder.getPromotionPropertyMap().get("scriptrunner_version"));
    
    assertEquals("Additional promotion property should be set from manifest override", "1", mManifestBuilder.getPromotionPropertyMap().get("add_prop"));
    
  }
  
}
