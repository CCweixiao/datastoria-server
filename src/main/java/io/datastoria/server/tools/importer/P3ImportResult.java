package io.datastoria.server.tools.importer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Outcome of a single importer run. Captures per-table inserted/updated/skipped counts, a flat list
 * of row-level errors (file + line + message), and a checksum delta against the manifest.
 *
 * <p>Use {@link #isSuccess()} as the overall pass/fail gate: the importer is strict on row counts
 * (any mismatch fails the run) but tolerant of individual bad rows (they are reported as errors and
 * the row is skipped). Tune the threshold by enabling {@code --strict-rows} in a future iteration
 * if a single dropped row should fail the whole import.
 */
public record P3ImportResult(
    boolean dryRun,
    Map<String, Long> inserted,
    Map<String, Long> updated,
    Map<String, Long> skipped,
    List<RowError> errors,
    ChecksumDelta checksum) {

  public record RowError(String file, long line, String messageId, String message) {}

  public record ChecksumDelta(
      Map<String, Long> expected, Map<String, Long> actual, boolean matches) {}

  public boolean isSuccess() {
    return errors.isEmpty() && checksum.matches();
  }

  public long totalInserted() {
    return inserted.values().stream().mapToLong(Long::longValue).sum();
  }

  public long totalUpdated() {
    return updated.values().stream().mapToLong(Long::longValue).sum();
  }

  public long totalErrors() {
    return errors.size();
  }

  /** Mutable builder used during import so we can accumulate counts as rows are processed. */
  public static Builder builder(boolean dryRun) {
    return new Builder(dryRun);
  }

  public static final class Builder {
    private final boolean dryRun;
    private final Map<String, Long> inserted = new java.util.HashMap<>();
    private final Map<String, Long> updated = new java.util.HashMap<>();
    private final Map<String, Long> skipped = new java.util.HashMap<>();
    private final List<RowError> errors = new ArrayList<>();

    private Builder(boolean dryRun) {
      this.dryRun = dryRun;
      for (String t :
          new String[] {
            P3ImportManifest.SESSIONS,
            P3ImportManifest.MESSAGES,
            P3ImportManifest.FEEDBACK,
            P3ImportManifest.SHARES
          }) {
        inserted.put(t, 0L);
        updated.put(t, 0L);
        skipped.put(t, 0L);
      }
    }

    public Builder inserted(String table) {
      inserted.merge(table, 1L, Long::sum);
      return this;
    }

    public Builder updated(String table) {
      updated.merge(table, 1L, Long::sum);
      return this;
    }

    public Builder skipped(String table) {
      skipped.merge(table, 1L, Long::sum);
      return this;
    }

    public Builder error(String file, long line, String messageId, String message) {
      errors.add(new RowError(file, line, messageId, message));
      return this;
    }

    public P3ImportResult build(ChecksumDelta checksum) {
      return new P3ImportResult(
          dryRun,
          Map.copyOf(inserted),
          Map.copyOf(updated),
          Map.copyOf(skipped),
          List.copyOf(errors),
          checksum);
    }
  }
}
