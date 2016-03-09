package com.fivium.scriptrunner2;

/**
 * Enumeration of all available command line options.
 */
public enum CommandLineOption {
    BUILD("build")
  , RUN("run")
  , INSTALL("install")
  , UPDATE("update")
  , PARSE_SCRIPTS("parse")
  , NO_EXEC("noexec")
  , LOG_DIRECTORY("logdir")
  , LOG_STANDARD_OUT("logstdout")
  , LOG_DEBUG("logdebug")
  , SKIP_VERSION_CHECK("noversioncheck")
  , SKIP_HASH_CHECK("nohashcheck")
  , PROMOTE_USER("user")
  , PROMOTE_PASSWORD("password")
  , JDBC_CONNECT_STRING("jdbc")
  , DB_HOST("host")
  , DB_PORT("port")
  , DB_SID("sid")
  , DB_SERVICE_NAME("service")
  , DB_SYSDBA("sysdba")
  , OUTPUT_FILE_PATH("outfile")
  , PROMOTION_LABEL("label")
  , ADDITIONAL_PROPERTIES("props")
  , INSTALL_PROMOTE_USER("newpromoteuser")
  , INSTALL_PROMOTE_PASSWORD("newpromotepassword")
  , NO_UNIMPLICATED_FILES("nounimplicatedfiles");

  private final String mArgString;

  private CommandLineOption(String pArgString){
    mArgString = pArgString;
  }

  /**
   * Gets the command line argument string which this enum represents.
   * @return Argument string.
   */
  public String getArgString(){
    return mArgString;
  }

}
