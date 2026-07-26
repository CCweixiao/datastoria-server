package io.datastoria.server.persistence.typehandler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Calendar;

import org.junit.jupiter.api.Test;

class InstantTypeHandlerTest {

  private final InstantTypeHandler handler = new InstantTypeHandler();

  @Test
  void writesIsoTextForSqlite() throws Exception {
    PreparedStatement statement = statementFor("SQLite");
    Instant value = Instant.parse("2026-07-26T09:10:11.123456Z");

    handler.setNonNullParameter(statement, 1, value, null);

    verify(statement).setString(1, "2026-07-26T09:10:11.123Z");
    verify(statement, never()).setTimestamp(any(Integer.class), any(), any());
  }

  @Test
  void writesUtcTimestampForMysql() throws Exception {
    PreparedStatement statement = statementFor("MySQL");
    Instant value = Instant.parse("2026-07-26T09:10:11.123456Z");

    handler.setNonNullParameter(statement, 2, value, null);

    verify(statement)
        .setTimestamp(
            eq(2),
            eq(Timestamp.from(Instant.parse("2026-07-26T09:10:11.123Z"))),
            any(Calendar.class));
    verify(statement, never()).setString(any(Integer.class), any());
  }

  private static PreparedStatement statementFor(String productName) throws Exception {
    PreparedStatement statement = mock(PreparedStatement.class);
    Connection connection = mock(Connection.class);
    DatabaseMetaData metadata = mock(DatabaseMetaData.class);
    when(statement.getConnection()).thenReturn(connection);
    when(connection.getMetaData()).thenReturn(metadata);
    when(metadata.getDatabaseProductName()).thenReturn(productName);
    return statement;
  }
}
