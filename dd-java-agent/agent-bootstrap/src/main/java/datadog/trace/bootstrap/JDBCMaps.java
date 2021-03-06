package datadog.trace.bootstrap;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import lombok.Data;

/**
 * JDBC instrumentation shares a global map of connection info.
 *
 * <p>In the bootstrap project to ensure visibility by all classes.
 */
public class JDBCMaps {
  public static final Map<Connection, DBInfo> connectionInfo =
      Collections.synchronizedMap(new WeakHashMap<Connection, DBInfo>());
  public static final Map<PreparedStatement, String> preparedStatements =
      Collections.synchronizedMap(new WeakHashMap<PreparedStatement, String>());

  public static final String DB_QUERY = "DB Query";

  @Data
  public static class DBInfo {
    public static DBInfo DEFAULT = new DBInfo("null", "database", null);
    private final String url;
    private final String type;
    private final String user;
  }
}
