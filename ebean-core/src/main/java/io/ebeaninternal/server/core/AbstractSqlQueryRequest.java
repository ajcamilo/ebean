package io.ebeaninternal.server.core;

import io.ebean.EbeanServer;
import io.ebean.Transaction;
import io.ebean.util.JdbcClose;
import io.ebeaninternal.api.BindParams;
import io.ebeaninternal.api.SpiEbeanServer;
import io.ebeaninternal.api.SpiQuery;
import io.ebeaninternal.api.SpiSqlBinding;
import io.ebeaninternal.api.SpiTransaction;
import io.ebeaninternal.server.lib.Str;
import io.ebeaninternal.server.persist.Binder;
import io.ebeaninternal.server.persist.TrimLogSql;
import io.ebeaninternal.server.util.BindParamsParser;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Wraps the objects involved in executing a SQL / Relational Query.
 */
public abstract class AbstractSqlQueryRequest {

  protected final SpiSqlBinding query;

  protected final SpiEbeanServer server;

  protected SpiTransaction transaction;

  private boolean createdTransaction;

  protected String sql;

  protected ResultSet resultSet;

  protected String bindLog = "";

  protected PreparedStatement pstmt;

  protected long startNano;

  /**
   * Create the BeanFindRequest.
   */
  AbstractSqlQueryRequest(SpiEbeanServer server, SpiSqlBinding query, Transaction t) {
    this.server = server;
    this.query = query;
    this.transaction = (SpiTransaction) t;
  }

  /**
   * Create a transaction if none currently exists.
   */
  public void initTransIfRequired() {
    if (transaction == null) {
      transaction = server.currentServerTransaction();
      if (transaction == null || !transaction.isActive()) {
        // create a local readOnly transaction
        transaction = server.createReadOnlyTransaction(null);
        createdTransaction = true;
      }
    }
  }

  /**
   * End the transaction if it was locally created.
   */
  public void endTransIfRequired() {
    if (createdTransaction) {
      transaction.commit();
    }
  }

  protected void flushJdbcBatchOnQuery() {
    if (transaction.isFlushOnQuery()) {
      transaction.flush();
    }
  }

  public boolean isLogSql() {
    return transaction.isLogSql();
  }

  /**
   * Set the resultSet and associated query plan if known.
   */
  abstract void setResultSet(ResultSet resultSet, Object queryPlanKey) throws SQLException;

  /**
   * Return the bindLog for this request.
   */
  public String getBindLog() {
    return bindLog;
  }

  /**
   * Return true if we can navigate to the next row.
   */
  public boolean next() throws SQLException {
    return resultSet.next();
  }

  protected abstract void requestComplete();

  /**
   * Close the underlying resources.
   */
  public void close() {
    requestComplete();
    JdbcClose.close(resultSet);
    JdbcClose.close(pstmt);
  }


  /**
   * Prepare the SQL taking into account named bind parameters.
   */
  private void prepareSql() {
    String sql = query.getQuery();
    BindParams bindParams = query.getBindParams();
    if (!bindParams.isEmpty()) {
      // convert any named parameters if required
      sql = BindParamsParser.parse(bindParams, sql);
    }
    this.sql = limitOffset(sql);
  }

  private String limitOffset(String sql) {
    int firstRow = query.getFirstRow();
    int maxRows = query.getMaxRows();
    if (firstRow > 0 || maxRows > 0) {
      return server.getDatabasePlatform().getBasicSqlLimiter().limit(sql, firstRow, maxRows);
    }
    return sql;
  }

  /**
   * Prepare and execute the SQL using the Binder.
   */
  public void executeSql(Binder binder, SpiQuery.Type type) throws SQLException {
    startNano = System.nanoTime();
    executeAsSql(binder);
  }

  protected void executeAsSql(Binder binder) throws SQLException {
    prepareSql();
    Connection conn = transaction.getInternalConnection();
    pstmt = conn.prepareStatement(sql);
    if (query.getTimeout() > 0) {
      pstmt.setQueryTimeout(query.getTimeout());
    }
    if (query.getBufferFetchSizeHint() > 0) {
      pstmt.setFetchSize(query.getBufferFetchSizeHint());
    }
    BindParams bindParams = query.getBindParams();
    if (!bindParams.isEmpty()) {
      this.bindLog = binder.bind(bindParams, pstmt, conn);
    }
    if (isLogSql()) {
      transaction.logSql(Str.add(TrimLogSql.trim(sql), "; --bind(", bindLog, ")"));
    }

    setResultSet(pstmt.executeQuery(), null);
  }

  /**
   * Return the SQL executed for this query.
   */
  public String getSql() {
    return sql;
  }

}
