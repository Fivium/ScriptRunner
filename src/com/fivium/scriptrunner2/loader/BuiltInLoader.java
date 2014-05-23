package com.fivium.scriptrunner2.loader;

import java.util.HashMap;
import java.util.Map;

/**
 * Marker class for all built-in loaders in ScriptRunner. This provides static methods for retriving the singleton
 * built-in loaders.
 */
public abstract class BuiltInLoader
implements Loader {
  
  public static final String LOADER_NAME_DB_SOURCE = "DatabaseSource";
  public static final String LOADER_NAME_SCRIPTRUNNER_UTIL = "ScriptRunnerUtil";
  public static final String LOADER_NAME_PATCH = "Patch";
  /** Special loader name for the builder only - files associated with this loader are ignored when constructing the manifest. */
  public static final String LOADER_NAME_IGNORE = "Ignore";
  
  private static final Map<String, Loader> gBuiltInLoaderMap;
  static {
    gBuiltInLoaderMap = new HashMap<String, Loader>();
    gBuiltInLoaderMap.put(LOADER_NAME_DB_SOURCE, new DatabaseSourceLoader());
    gBuiltInLoaderMap.put(LOADER_NAME_SCRIPTRUNNER_UTIL, UtilLoader.getInstance());
    gBuiltInLoaderMap.put(LOADER_NAME_PATCH, new PatchScriptLoader());
  }
  
  /**
   * Get the built in loader corresponding to the given name, or null if it does not exist.
   * @param pLoaderName Name of required loader.
   * @return Built-in loader.
   */
  public static Loader getBuiltInLoaderOrNull(String pLoaderName){
    return gBuiltInLoaderMap.get(pLoaderName);
  }

  /**
   * Gets the map of built-in loaders. The key of the map is the loader name.
   * @return Map of names to built-in loaders.
   */
  public static Map<String, Loader> getBuiltInLoaderMap() {
    return gBuiltInLoaderMap;
  }

}
