package com.fivium.scriptrunner2;


import com.fivium.scriptrunner2.database.DatabaseConnection;
import com.fivium.scriptrunner2.ex.ExFatalError;
import com.fivium.scriptrunner2.util.ScriptRunnerVersion;
import com.fivium.scriptrunner2.util.XFUtil;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;


/**
 * Wrapper for the Apache Commons CLI library which allows command line options to be resolved by an enum rather than a String.
 * Also provides the ability to override or set options which were not originally specified on the command line. <br/><br/>
 *
 * Static methods are also available for reading arguments and passwords from standard in.
 */
public class CommandLineWrapper {
  
  private static final Options gCommandLineOptions;
  static {
    //Setup for Apache CLI 
    gCommandLineOptions = new Options();
    
    Option lBuildOption = new Option(CommandLineOption.BUILD.getArgString(), true, "Builds a promotion archive from the given source directory.");
    Option lRunOption = new Option(CommandLineOption.RUN.getArgString(), true, "Runs a promotion from given source archive or directory.");
    Option lInstallOption = new Option(CommandLineOption.INSTALL.getArgString(), false, "Installs ScriptRunner metadata tables (requires SYSDBA privileges).");
    Option lUpdateOption = new Option(CommandLineOption.UPDATE.getArgString(), false, "Updates ScriptRunner metadata tables to the latest version.");
    Option lParseOption = new Option(CommandLineOption.PARSE_SCRIPTS.getArgString(), false, "Parses patch scripts and outputs the result to standard out.");
    lParseOption.setArgs(999);
    
    OptionGroup lStartOptionGroup = new OptionGroup();
    lStartOptionGroup.addOption(lBuildOption);
    lStartOptionGroup.addOption(lRunOption);
    lStartOptionGroup.addOption(lInstallOption);
    lStartOptionGroup.addOption(lUpdateOption);
    lStartOptionGroup.addOption(lParseOption);
    lStartOptionGroup.setRequired(true);
    
    gCommandLineOptions.addOptionGroup(lStartOptionGroup);
    
    gCommandLineOptions.addOption(CommandLineOption.LOG_DIRECTORY.getArgString(), true, "Directory to write log file to. Default is current directory.");
    gCommandLineOptions.addOption(CommandLineOption.LOG_STANDARD_OUT.getArgString(), false, "Log all output to standard out in addition to the log file.");
    gCommandLineOptions.addOption(CommandLineOption.LOG_DEBUG.getArgString(), false, "Turns verbose debug logging on.");
    
    gCommandLineOptions.addOption(CommandLineOption.SKIP_VERSION_CHECK.getArgString(), false, "(Run only) Skips the ScriptRunner version verification.");
    
    gCommandLineOptions.addOption(CommandLineOption.SKIP_HASH_CHECK.getArgString(), false, "(Run only) Skips checking file hashes against entries the manifest");
    
    gCommandLineOptions.addOption(CommandLineOption.NO_EXEC.getArgString(), false, "(Run only) Does not execute the promote but produces output showing what would be run.");
    
    gCommandLineOptions.addOption(CommandLineOption.PROMOTE_USER.getArgString(), true, "Specify the database user to connect as (default is " + DatabaseConnection.DEFAULT_PROMOTE_USER + ")");
    gCommandLineOptions.addOption(CommandLineOption.PROMOTE_PASSWORD.getArgString(), true, "Specify the password for the database user. If not specified this will be prompted for.");
    
    gCommandLineOptions.addOption(CommandLineOption.JDBC_CONNECT_STRING.getArgString(), true, "A full JDBC connect string for establishing a database connection.");

    gCommandLineOptions.addOption(CommandLineOption.INSTALL_PROMOTE_USER.getArgString(), true, "(install only) The new promotion user to create.");
    gCommandLineOptions.addOption(CommandLineOption.INSTALL_PROMOTE_PASSWORD.getArgString(), true, "(install only) The password to use for the new promote user.");
    
    gCommandLineOptions.addOption(CommandLineOption.DB_HOST.getArgString(), true, "Database hostname.");
    gCommandLineOptions.addOption(CommandLineOption.DB_PORT.getArgString(), true, "Database port.");
    
    
    Option lSidOption = new Option(CommandLineOption.DB_SID.getArgString(), true, "Database SID.");
    Option lServiceNameOption = new Option(CommandLineOption.DB_SERVICE_NAME.getArgString(), true, "Database service name.");
    OptionGroup lSidServiceNameGroup = new OptionGroup();
    lSidServiceNameGroup.addOption(lSidOption);
    lSidServiceNameGroup.addOption(lServiceNameOption);
    
    gCommandLineOptions.addOptionGroup(lSidServiceNameGroup);
    
    gCommandLineOptions.addOption(CommandLineOption.DB_SYSDBA.getArgString(), false, "Connect to the database as SYSDBA.");
    
    gCommandLineOptions.addOption(CommandLineOption.OUTPUT_FILE_PATH.getArgString(), true, "(Build only) File path where the output will be written to. Default is {CURRENT_DIR}/{PROMOTE_LABEL}.zip");
    gCommandLineOptions.addOption(CommandLineOption.PROMOTION_LABEL.getArgString(), true, "(Build only) Promotion label for builder.");
    gCommandLineOptions.addOption(CommandLineOption.ADDITIONAL_PROPERTIES.getArgString(), true, "(Build only) Location of the additional properties file for the builder.");

    gCommandLineOptions.addOption(CommandLineOption.NO_UNIMPLICATED_FILES.getArgString(), false, "(Build only) Error (rather than warn) if files are found in source directory but not implicated by manifest builder rules.");
    
    //gCommandLineOptions.addOption("help", false, "Prints help.");
  }
  
  private final CommandLine mCommandLine;
  private final Map<CommandLineOption, String> mOverrideMap = new HashMap<CommandLineOption, String>();
  
  /**
   * Constructs a new wrapper object from the environment argument array.
   * @param pArgs Arg array.
   * @throws ParseException If the arg array could nto be parsed.
   */
  public CommandLineWrapper(String[] pArgs) 
  throws ParseException {    
    CommandLineParser lCLParser = new PosixParser();
    mCommandLine = lCLParser.parse(gCommandLineOptions, pArgs);
  }
  
  /**
   * Tests if the given option was specified in the original command line arguments.
   * @param pOption Option to test for.
   * @return True if the option was specified, false otherwise.
   */
  public boolean hasOption(CommandLineOption pOption){
    return mCommandLine.hasOption(pOption.getArgString());
  }
  
  /**
   * Gets the value for the given option.
   * @param pOption
   * @return Option value.
   */
  public String getOption(CommandLineOption pOption){
    String lOverride = mOverrideMap.get(pOption);
    return XFUtil.nvl(lOverride, mCommandLine.getOptionValue(pOption.getArgString()));    
  }
  
  /**
   * Gets the list of values for a multi-value option (original arguments only).
   * @param pOption
   * @return Option's values.
   */
  public List<String> getOptionValues(CommandLineOption pOption){    
    return Arrays.asList(mCommandLine.getOptionValues(pOption.getArgString()));
  }
  
  /**
   * Sets an option programtically, overriding it if it was previously set.
   * @param pOption Option to set or override.
   * @param pValue Value of the option.
   */
  public void overrideOption(CommandLineOption pOption, String pValue){
    mOverrideMap.put(pOption, pValue); 
  }
  
  /**
   * Prints formatted help text to standard out.
   */
  public static void printHelp(){    
    HelpFormatter lFormatter = new HelpFormatter();
    lFormatter.printHelp("java -jar ScriptRunner.jar", gCommandLineOptions);
  }
  
  /**
   * Reads a password from the command prompt, if a console is available. Otherwise an error is thrown. The prompt loops
   * until non-null input is received.
   * @param pPrompt Prompt used to ask for a password.
   * @return The entered password.
   */
  public static String readPassword(String pPrompt){
    if(System.console() == null) {
      //Hack for JDev debugging as it doesn't provide a console when running
      if("DEV".equals(ScriptRunnerVersion.getVersionNumber())){
        return readArg(pPrompt, false);
      }
      throw new ExFatalError("Could not locate a console. ScriptRunner must be invoked from an interactive command shell or have all arguments provided when invoked.");      
    }
    
    String lPassword = null;
    int lTries = 0;
    String lPrompt = pPrompt;
    while(XFUtil.isNull(lPassword)){
      if(lTries > 0){
        lPrompt = "[Password cannot be null, try again] " + pPrompt;
      }
      lPassword = new String(System.console().readPassword(lPrompt));
      lTries++;
    }
    return lPassword;
  }
  
  /**
   * Reads an argument from the console. If the argument is optional and the user specifies nothing, null is returned.
   * @param pPrompt Prompt to display to the user.
   * @param pOptional If true, the method returns null if the user enters an empty argument. Otherwise the user is re-prompted
   * until they provide non-null input.
   * @return The argument as entered by the user, or null.
   */
  public static String readArg(String pPrompt, boolean pOptional){    
    String lArg = null;
    int lTries = 0;
    String lPrompt = pPrompt;
    Scanner lScanner = new Scanner(System.in);
    while(XFUtil.isNull(lArg)){
      if(lTries > 0){
        if(pOptional){
          return null;
        }
        lPrompt = "[Argument cannot be null, try again] " + pPrompt;
      }
      System.out.println(lPrompt + ": ");    
      lArg = lScanner.nextLine();
      lTries++;
    }    
    //lScanner.close();
    return lArg;
  }
}
