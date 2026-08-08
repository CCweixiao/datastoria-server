package io.github.ccweixiao.datastoria.common.config;

import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.AssertFalse;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

/** Server-owned ClickHouse limits applied to every non-admin and Agent query. */
@Component
@ConfigurationProperties(prefix = "datastoria.clickhouse.query-security")
@Validated
public class ClickHouseQuerySecurityProperties {

  @AssertTrue private boolean readOnly;

  @AssertFalse private boolean allowDdl;

  @AssertFalse private boolean allowIntrospectionFunctions;

  @Min(1)
  private long maxExecutionTime;

  @Min(1)
  private long maxResultRows;

  @Min(1)
  private long maxResultBytes;

  @Min(1)
  private long maxRowsToRead;

  @Min(1)
  private long maxBytesToRead;

  @Min(1)
  private long maxMemoryUsage;

  @Min(1)
  private long maxThreads;

  @Pattern(regexp = "break")
  private String resultOverflowMode;

  @Pattern(regexp = "throw")
  private String readOverflowMode;

  public Map<String, Object> asClickHouseSettings() {
    return Map.ofEntries(
        Map.entry("readonly", readOnly ? 2 : 0),
        Map.entry("allow_ddl", allowDdl ? 1 : 0),
        Map.entry("allow_introspection_functions", allowIntrospectionFunctions ? 1 : 0),
        Map.entry("max_execution_time", maxExecutionTime),
        Map.entry("max_result_rows", maxResultRows),
        Map.entry("max_result_bytes", maxResultBytes),
        Map.entry("result_overflow_mode", resultOverflowMode),
        Map.entry("max_rows_to_read", maxRowsToRead),
        Map.entry("max_bytes_to_read", maxBytesToRead),
        Map.entry("read_overflow_mode", readOverflowMode),
        Map.entry("max_memory_usage", maxMemoryUsage),
        Map.entry("max_threads", maxThreads));
  }

  public boolean isReadOnly() {
    return readOnly;
  }

  public void setReadOnly(boolean readOnly) {
    this.readOnly = readOnly;
  }

  public boolean isAllowDdl() {
    return allowDdl;
  }

  public void setAllowDdl(boolean allowDdl) {
    this.allowDdl = allowDdl;
  }

  public boolean isAllowIntrospectionFunctions() {
    return allowIntrospectionFunctions;
  }

  public void setAllowIntrospectionFunctions(boolean value) {
    this.allowIntrospectionFunctions = value;
  }

  public long getMaxExecutionTime() {
    return maxExecutionTime;
  }

  public void setMaxExecutionTime(long value) {
    this.maxExecutionTime = value;
  }

  public long getMaxResultRows() {
    return maxResultRows;
  }

  public void setMaxResultRows(long value) {
    this.maxResultRows = value;
  }

  public long getMaxResultBytes() {
    return maxResultBytes;
  }

  public void setMaxResultBytes(long value) {
    this.maxResultBytes = value;
  }

  public long getMaxRowsToRead() {
    return maxRowsToRead;
  }

  public void setMaxRowsToRead(long value) {
    this.maxRowsToRead = value;
  }

  public long getMaxBytesToRead() {
    return maxBytesToRead;
  }

  public void setMaxBytesToRead(long value) {
    this.maxBytesToRead = value;
  }

  public long getMaxMemoryUsage() {
    return maxMemoryUsage;
  }

  public void setMaxMemoryUsage(long value) {
    this.maxMemoryUsage = value;
  }

  public long getMaxThreads() {
    return maxThreads;
  }

  public void setMaxThreads(long value) {
    this.maxThreads = value;
  }

  public String getResultOverflowMode() {
    return resultOverflowMode;
  }

  public void setResultOverflowMode(String value) {
    this.resultOverflowMode = value;
  }

  public String getReadOverflowMode() {
    return readOverflowMode;
  }

  public void setReadOverflowMode(String value) {
    this.readOverflowMode = value;
  }
}
