package io.github.ccweixiao.datastoria.agent.runtime;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Fail-closed lexical guard for Agent-generated ClickHouse SQL.
 *
 * <p>This is deliberately narrower than the ClickHouse grammar. It accepts query/introspection
 * statements and rejects ambiguous constructs rather than trying to prove every possible SQL
 * extension safe. ClickHouse's {@code readonly=2} setting remains the database-side second layer.
 */
public final class ClickHouseReadOnlySqlClassifier {

  private static final Set<String> ALLOWED_FIRST_KEYWORDS =
      Set.of("select", "with", "explain", "describe", "desc", "show", "exists");
  private static final Pattern PROHIBITED_KEYWORDS =
      Pattern.compile(
          "\\b(insert|alter|create|drop|truncate|optimize|rename|attach|detach|delete|update|"
              + "grant|revoke|backup|restore)\\b");
  private static final Pattern EXTERNAL_TABLE_FUNCTIONS =
      Pattern.compile(
          "\\b(url|file|s3|s3cluster|hdfs|hdfscluster|azureblobstorage|"
              + "azureblobstoragecluster|jdbc|mysql|postgresql|mongodb|redis|"
              + "remote|remotesecure|cluster|executable|"
              + "executablepool)\\s*\\(");
  private static final Pattern CLUSTER_ALL_REPLICAS =
      Pattern.compile("(?i)\\bclusterallreplicas\\s*\\(\\s*'((?:\\\\.|''|[^'])*)'\\s*,");
  private static final Pattern INTO_OUTFILE = Pattern.compile("\\binto\\s+outfile\\b");
  private static final Pattern FORMAT_CLAUSE = Pattern.compile("\\bformat\\s+[a-z0-9_]+\\s*$");
  private static final Pattern DANGEROUS_SETTINGS =
      Pattern.compile(
          "\\bsettings\\b[\\s\\S]*\\b(readonly|allow_ddl|allow_introspection_functions|"
              + "max_result_rows|max_result_bytes|result_overflow_mode|max_execution_time)\\b");

  public String requireReadOnly(String sql) {
    return requireReadOnly(sql, null);
  }

  public String requireReadOnly(String sql, String allowedCluster) {
    if (sql == null || sql.isBlank()) {
      throw new IllegalArgumentException("SQL must not be blank");
    }
    String masked = maskLiteralsAndComments(sql);
    String statement = stripSingleTerminalSemicolon(masked).trim();
    if (statement.isEmpty()) {
      throw new IllegalArgumentException("SQL must contain a statement");
    }
    if (statement.indexOf(';') >= 0) {
      throw new IllegalArgumentException("Multiple SQL statements are not allowed");
    }
    String normalized = statement.toLowerCase(Locale.ROOT);
    String firstKeyword = firstKeyword(normalized);
    if (!ALLOWED_FIRST_KEYWORDS.contains(firstKeyword)) {
      throw new IllegalArgumentException("Only read-only ClickHouse queries are allowed");
    }
    if (PROHIBITED_KEYWORDS.matcher(normalized).find()) {
      throw new IllegalArgumentException("SQL contains a prohibited mutation or control keyword");
    }
    if (EXTERNAL_TABLE_FUNCTIONS.matcher(normalized).find()) {
      throw new IllegalArgumentException("External and filesystem table functions are not allowed");
    }
    validateClusterAllReplicas(sql, masked, allowedCluster);
    if (INTO_OUTFILE.matcher(normalized).find()) {
      throw new IllegalArgumentException("INTO OUTFILE is not allowed");
    }
    if (FORMAT_CLAUSE.matcher(normalized).find()) {
      throw new IllegalArgumentException("Custom FORMAT clauses are not allowed");
    }
    if (DANGEROUS_SETTINGS.matcher(normalized).find()) {
      throw new IllegalArgumentException("Query cannot override server safety settings");
    }
    if ("with".equals(firstKeyword)
        && !Pattern.compile("\\bselect\\b").matcher(normalized).find()) {
      throw new IllegalArgumentException("WITH must resolve to a SELECT query");
    }
    return stripSingleTerminalSemicolon(sql).trim();
  }

  private static void validateClusterAllReplicas(
      String sql, String maskedSql, String allowedCluster) {
    var allCalls = Pattern.compile("(?i)\\bclusterallreplicas\\s*\\(").matcher(maskedSql);
    var authorizedCalls = CLUSTER_ALL_REPLICAS.matcher(sql);
    int callCount = 0;
    while (allCalls.find()) {
      callCount++;
    }
    int authorizedCount = 0;
    while (authorizedCalls.find()) {
      authorizedCount++;
      String requestedCluster =
          authorizedCalls.group(1).replace("''", "'").replace("\\'", "'").replace("\\\\", "\\");
      if (allowedCluster == null
          || allowedCluster.isBlank()
          || !allowedCluster.equals(requestedCluster)) {
        throw new IllegalArgumentException(
            "clusterAllReplicas is only allowed for the configured ClickHouse cluster");
      }
    }
    if (callCount != authorizedCount) {
      throw new IllegalArgumentException(
          "clusterAllReplicas requires a literal configured ClickHouse cluster name");
    }
  }

  private static String firstKeyword(String sql) {
    int index = 0;
    while (index < sql.length() && !Character.isLetter(sql.charAt(index))) {
      index++;
    }
    int start = index;
    while (index < sql.length() && Character.isLetter(sql.charAt(index))) {
      index++;
    }
    return sql.substring(start, index);
  }

  private static String stripSingleTerminalSemicolon(String sql) {
    int end = sql.length();
    while (end > 0 && Character.isWhitespace(sql.charAt(end - 1))) {
      end--;
    }
    if (end > 0 && sql.charAt(end - 1) == ';') {
      return sql.substring(0, end - 1) + sql.substring(end);
    }
    return sql;
  }

  static String maskLiteralsAndComments(String sql) {
    StringBuilder result = new StringBuilder(sql.length());
    State state = State.NORMAL;
    for (int index = 0; index < sql.length(); index++) {
      char current = sql.charAt(index);
      char next = index + 1 < sql.length() ? sql.charAt(index + 1) : '\0';
      switch (state) {
        case NORMAL -> {
          if (current == '\'') {
            result.append(' ');
            state = State.SINGLE_QUOTE;
          } else if (current == '"') {
            result.append(' ');
            state = State.DOUBLE_QUOTE;
          } else if (current == '`') {
            result.append(' ');
            state = State.BACKTICK;
          } else if (current == '-' && next == '-') {
            result.append("  ");
            index++;
            state = State.LINE_COMMENT;
          } else if (current == '#') {
            result.append(' ');
            state = State.LINE_COMMENT;
          } else if (current == '/' && next == '*') {
            result.append("  ");
            index++;
            state = State.BLOCK_COMMENT;
          } else {
            result.append(current);
          }
        }
        case SINGLE_QUOTE -> {
          result.append(' ');
          if (current == '\\' && next != '\0') {
            result.append(' ');
            index++;
          } else if (current == '\'' && next == '\'') {
            result.append(' ');
            index++;
          } else if (current == '\'') {
            state = State.NORMAL;
          }
        }
        case DOUBLE_QUOTE -> {
          result.append(' ');
          if (current == '"' && next == '"') {
            result.append(' ');
            index++;
          } else if (current == '"') {
            state = State.NORMAL;
          }
        }
        case BACKTICK -> {
          result.append(' ');
          if (current == '`' && next == '`') {
            result.append(' ');
            index++;
          } else if (current == '`') {
            state = State.NORMAL;
          }
        }
        case LINE_COMMENT -> {
          result.append(current == '\n' || current == '\r' ? current : ' ');
          if (current == '\n' || current == '\r') {
            state = State.NORMAL;
          }
        }
        case BLOCK_COMMENT -> {
          result.append(' ');
          if (current == '*' && next == '/') {
            result.append(' ');
            index++;
            state = State.NORMAL;
          }
        }
      }
    }
    if (state == State.SINGLE_QUOTE
        || state == State.DOUBLE_QUOTE
        || state == State.BACKTICK
        || state == State.BLOCK_COMMENT) {
      throw new IllegalArgumentException("SQL contains an unterminated literal or comment");
    }
    return result.toString();
  }

  private enum State {
    NORMAL,
    SINGLE_QUOTE,
    DOUBLE_QUOTE,
    BACKTICK,
    LINE_COMMENT,
    BLOCK_COMMENT
  }
}
