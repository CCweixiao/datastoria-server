package io.github.ccweixiao.datastoria.dao.persistence.typehandler;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Calendar;
import java.util.TimeZone;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

/**
 * Unified cross-dialect {@link Instant} type handler for TEXT/datetime(6) timestamp columns.
 *
 * <p>SQLite stores ISO-8601 TEXT; MySQL stores datetime(6) ({@code yyyy-MM-dd HH:mm:ss.SSSSSS}).
 * Both drivers accept and return these values as strings, so the instant is serialised as a string.
 *
 * <p>Writes always truncate to <em>millisecond</em> precision. This matches the opaque keyset
 * cursor wire format ({@link io.github.ccweixiao.datastoria.dao.repository.SessionListCursor}),
 * which is millisecond precise, so SQLite's lexicographic TEXT comparison never re-includes the
 * cursor's own row on the next page. It also keeps sort order and cursor semantics identical across
 * SQLite and MySQL.
 *
 * <p>Reads accept both the ISO-8601 form and the MySQL datetime(6) form (space separator, no zone
 * suffix), interpreting the latter as UTC.
 */
@MappedTypes(Instant.class)
public class InstantTypeHandler extends BaseTypeHandler<Instant> {

  private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

  @Override
  public void setNonNullParameter(PreparedStatement ps, int i, Instant parameter, JdbcType jdbcType)
      throws SQLException {
    Instant value = parameter.truncatedTo(ChronoUnit.MILLIS);
    if (isMySql(ps)) {
      // DATETIME has no timezone and TIMESTAMP conversion otherwise depends on the connection's
      // session timezone. Supplying an explicit UTC calendar makes both column types deterministic
      // and avoids asking MySQL to parse SQLite's ISO value with a trailing 'Z'.
      ps.setTimestamp(i, Timestamp.from(value), Calendar.getInstance(UTC));
      return;
    }
    ps.setString(i, value.toString());
  }

  @Override
  public Instant getNullableResult(ResultSet rs, String columnName) throws SQLException {
    return parse(rs.getString(columnName));
  }

  @Override
  public Instant getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
    return parse(rs.getString(columnIndex));
  }

  @Override
  public Instant getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
    return parse(cs.getString(columnIndex));
  }

  private static Instant parse(String value) {
    if (value == null) {
      return null;
    }
    try {
      return Instant.parse(value);
    } catch (DateTimeParseException e) {
      // MySQL datetime(6) uses a space separator without a timezone suffix.
      return LocalDateTime.parse(value.replace(" ", "T")).toInstant(ZoneOffset.UTC);
    }
  }

  private static boolean isMySql(PreparedStatement statement) throws SQLException {
    String productName = statement.getConnection().getMetaData().getDatabaseProductName();
    return productName != null && productName.equalsIgnoreCase("MySQL");
  }
}
