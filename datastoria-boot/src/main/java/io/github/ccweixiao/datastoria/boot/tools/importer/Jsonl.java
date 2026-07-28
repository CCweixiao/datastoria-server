package io.github.ccweixiao.datastoria.boot.tools.importer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Stream a JSONL file line-by-line, parsing each non-blank line into the target type via the
 * supplied Jackson {@link ObjectMapper}. Blank lines are silently skipped (some exporters emit
 * trailing newlines that produce empty rows). Parse failures are routed to {@code onError}, letting
 * the caller decide whether to abort or accumulate; successful rows go to {@code onRow} with the
 * 1-indexed line number.
 *
 * <p>Returns the total number of physical lines processed (including blank lines) so callers can
 * report progress accurately. The file is closed via try-with-resources even on exception.
 */
public final class Jsonl {

  private Jsonl() {}

  /**
   * Read a JSONL file and dispatch each parsed row to {@code onRow}, each parse failure to {@code
   * onError}. Returns the count of physical lines read. Parse failures carry a human-readable
   * message via {@link JsonProcessingException#getOriginalMessage()} so callers don't need to cast.
   */
  public static <T> long readLines(
      Path file,
      ObjectMapper mapper,
      Class<T> type,
      BiConsumer<Long, T> onRow,
      TriConsumer<Long, String, String> onError)
      throws IOException {

    long lineNumber = 0L;
    try (Stream<String> lines = Files.lines(file, StandardCharsets.UTF_8)) {
      for (String raw : lines.toList()) {
        lineNumber++;
        if (raw.isBlank()) {
          continue;
        }
        try {
          T row = mapper.readValue(raw, type);
          onRow.accept(lineNumber, row);
        } catch (JsonProcessingException e) {
          onError.accept(lineNumber, raw, e.getOriginalMessage());
        }
      }
    }
    return lineNumber;
  }

  /** Count physical lines in a file without parsing; used for progress/manifest verification. */
  public static long countLines(Path file) throws IOException {
    try (Stream<String> lines = Files.lines(file, StandardCharsets.UTF_8)) {
      return lines.filter(s -> !s.isBlank()).count();
    }
  }

  /** Read the manifest.json at the directory root; throws if missing or unparseable. */
  public static P3ImportManifest readManifest(Path dir, ObjectMapper mapper) throws IOException {
    Path manifestPath = dir.resolve("manifest.json");
    if (!Files.exists(manifestPath)) {
      throw new IOException("manifest.json not found in " + dir);
    }
    try {
      return mapper.readValue(
          Files.readString(manifestPath, StandardCharsets.UTF_8), P3ImportManifest.class);
    } catch (JsonProcessingException e) {
      throw new IOException("Failed to parse manifest.json: " + e.getOriginalMessage(), e);
    }
  }

  /**
   * Write a list of objects as one JSON-per-line to {@code file}. Used by tests to build inputs.
   */
  public static <T> void writeLines(Path file, List<T> rows, ObjectMapper mapper)
      throws IOException {
    List<String> encoded = new ArrayList<>(rows.size());
    for (T r : rows) {
      encoded.add(mapper.writeValueAsString(r));
    }
    Files.writeString(file, String.join("\n", encoded) + "\n", StandardCharsets.UTF_8);
  }

  /** Tri-consumer equivalent so we can pass (line, raw, message) without allocating a list. */
  @FunctionalInterface
  public interface TriConsumer<A, B, C> {
    void accept(A a, B b, C c);
  }
}
