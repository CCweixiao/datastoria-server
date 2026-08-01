package io.github.ccweixiao.datastoria.dao.persistence.typehandler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Calendar;

import org.junit.jupiter.api.Test;

class InstantTypeHandlerTest {

  private final InstantTypeHandler handler = new InstantTypeHandler();

  @Test
  void writesUtcTimestampForMysql() throws Exception {
    PreparedStatement statement = mock(PreparedStatement.class);
    Instant value = Instant.parse("2026-07-26T09:10:11.123456Z");

    handler.setNonNullParameter(statement, 2, value, null);

    verify(statement)
        .setTimestamp(
            eq(2),
            eq(Timestamp.from(Instant.parse("2026-07-26T09:10:11.123Z"))),
            any(Calendar.class));
  }
}
