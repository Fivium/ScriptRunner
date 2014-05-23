package com.fivium.scriptrunner2.database.sql;


import com.fivium.scriptrunner2.ex.ExInternal;

import java.io.IOException;

import java.math.BigDecimal;

import java.sql.Connection;
import java.sql.SQLException;

import java.util.Map;

import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.ResultSetHandler;
import org.apache.commons.dbutils.handlers.MapHandler;
import org.apache.commons.dbutils.handlers.ScalarHandler;
import org.apache.commons.io.IOUtils;


/**
 * Utility class for accessing and executing internal SQL files. SQL files in this package should be accessed by methods
 * on this class.
 */
public class SQLManager {
  
  public static final String SQL_FILE_SELECT_PROMOTION_RUN_COUNT = "SelectPromotionRunCount.sql";  
  public static final String SQL_FILE_SELECT_PROMOTION_FILE_COUNT = "SelectPromotionFileCount.sql";  
  
  public static final String SQL_FILE_SELECT_PATCH_RUN_COUNT = "SelectPatchRunCount.sql";  
  public static final String SQL_FILE_INSERT_PATCH_RUN = "InsertPatchRun.sql";
  public static final String SQL_FILE_UPDATE_PATCH_RUN = "UpdatePatchRun.sql";
  
  public static final String SQL_FILE_SELECT_PATCH_RUN_STATEMENT_COUNT = "SelectPatchRunStatementCount.sql";  
  public static final String SQL_FILE_INSERT_PATCH_RUN_STATEMENT = "InsertPatchRunStatement.sql";
  public static final String SQL_FILE_UPDATE_PATCH_RUN_STATEMENT = "UpdatePatchRunStatement.sql";

  public static final String SQL_FILE_INSERT_PROMOTION_FILE = "InsertPromotionFile.sql";
  public static final String SQL_FILE_UPDATE_PROMOTION_FILE = "UpdatePromotionFile.sql";
  
  public static final String SQL_FILE_INSERT_PROMOTION_RUN = "InsertPromotionRun.sql";
  public static final String SQL_FILE_UPDATE_PROMOTION_RUN = "UpdatePromotionRun.sql";
  
  public static final String SQL_FILE_VERSION_CHECK = "ScriptRunnerVersionCheck.sql";
  
  /**
   * Gets the SQL String from the contents of the file specified.
   * @param pSQLFileName Filename string (see constants on this class).
   * @return SQL string.
   */
  public static String getSQLByName(String pSQLFileName){
    String lFileString;
    try {
      lFileString = IOUtils.toString(SQLManager.class.getResourceAsStream(pSQLFileName));
    }
    catch (IOException e) {
      throw new ExInternal("Error reading SQL file " + pSQLFileName, e);
    }
    return lFileString;
  }
  
  /**
   * Executes a SQL DDL/DML statement.
   * @param pDBConnection Connection to use.
   * @param pSQLFileName Name of SQL file to execute (see constants on this class).
   * @param pParams Params to bind into the statement.
   * @return Number of rows affected.
   * @throws SQLException If the query cannot be executed.
   */
  public static int executeUpdate(Connection pDBConnection, String pSQLFileName, Object... pParams) 
  throws SQLException {
    String lSQLString = getSQLByName(pSQLFileName);
    return new QueryRunner().update(pDBConnection, lSQLString, pParams);
  }
  
  /**
   * Runs a query which returns a single integer result.
   * @param pDBConnection Connection to use.
   * @param pSQLFileName Name of SQL file to execute (see constants on this class).
   * @param pParams Params to bind into the statement.
   * @return The integer result of running the query.
   * @throws SQLException If the query cannot be executed.
   */
  public static int queryScalarInt(Connection pDBConnection, String pSQLFileName, Object... pParams) 
  throws SQLException {
    String lSQLString = getSQLByName(pSQLFileName);
    ResultSetHandler<BigDecimal> lHandler = new ScalarHandler<BigDecimal>();
    return new QueryRunner().query(pDBConnection, lSQLString, lHandler, pParams).intValue();
  }
  
  /**
   * Runs a query which returns a single row presented as a map of column names to values.
   * @param pDBConnection Connection to use.
   * @param pSQLFileName Name of SQL file to execute (see constants on this class).
   * @param pParams Params to bind into the statement.
   * @return Map result
   * @throws SQLException If the query cannot be executed.
   */
  public static Map<String, Object> queryMap(Connection pDBConnection, String pSQLFileName, Object... pParams) 
  throws SQLException {
    String lSQLString = getSQLByName(pSQLFileName);
    ResultSetHandler<Map<String, Object>> lHandler = new MapHandler();
    return new QueryRunner().query(pDBConnection, lSQLString, lHandler, pParams);
  }
  
}
