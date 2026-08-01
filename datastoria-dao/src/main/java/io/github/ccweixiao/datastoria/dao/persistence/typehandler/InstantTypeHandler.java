package io.github.ccweixiao.datastoria.dao.persistence.typehandler;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Calendar;
import java.util.TimeZone;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

/**
 * MySQL {@link Instant} type handler for datetime(6) timestamp columns.
 *
 * <p>Writes always truncate to <em>millisecond</em> precision. This matches the opaque keyset
 * cursor wire format ({@link io.github.ccweixiao.datastoria.dao.repository.SessionListCursor}). An
 * explicit UTC calendar keeps DATETIME handling independent of the JVM and MySQL session timezones.
 */
@MappedTypes(Instant.class)
public class InstantTypeHandler extends BaseTypeHandler<Instant> {

  private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

  @Override
  public void setNonNullParameter(PreparedStatement ps, int i, Instant parameter, JdbcType jdbcType)
      throws SQLException {
    Instant value = parameter.truncatedTo(ChronoUnit.MILLIS);
    ps.setTimestamp(i, Timestamp.from(value), Calendar.getInstance(UTC));
  }

  @Override
  public Instant getNullableResult(ResultSet rs, String columnName) throws SQLException {
    return toInstant(rs.getTimestamp(columnName, Calendar.getInstance(UTC)));
  }

  @Override
  public Instant getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
    return toInstant(rs.getTimestamp(columnIndex, Calendar.getInstance(UTC)));
  }

  @Override
  public Instant getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
    return toInstant(cs.getTimestamp(columnIndex, Calendar.getInstance(UTC)));
  }

  private static Instant toInstant(Timestamp value) {
    return value == null ? null : value.toInstant();
  }
}
