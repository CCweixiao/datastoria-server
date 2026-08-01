package io.github.ccweixiao.datastoria.boot.tools.importer;

import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.ccweixiao.datastoria.dao.persistence.mapper.P3ImportMapper;

/**
 * Boots the importer as a one-shot CLI command on top of the standard Spring Boot application.
 *
 * <p>Activate by running the built jar with:
 *
 * <pre>{@code
 * java -jar datastoria-server.jar \
 *   --spring.profiles.active=dev \
 *   --p3.import.path=./data \
 *   --p3.import.dry-run=true
 * }</pre>
 *
 * <p>Exit codes:
 *
 * <ul>
 *   <li>{@code 0} — import succeeded or dry-run finished with no errors.
 *   <li>{@code 1} — one or more rows failed to parse or the manifest checksum didn't match.
 *   <li>{@code 2} — the input directory or manifest could not be read.
 * </ul>
 *
 * The bean is gated by {@code p3.import.path} so the runner is inert in normal server operation.
 */
@Component
@ConditionalOnProperty(prefix = "p3.import", name = "path")
public class P3ImportRunner implements CommandLineRunner, ExitCodeGenerator {

  private static final Logger log = LoggerFactory.getLogger(P3ImportRunner.class);

  private final P3Importer importer;
  private final org.springframework.core.env.Environment env;

  private int exitCode = 0;

  public P3ImportRunner(
      P3ImportMapper db,
      TransactionTemplate transactions,
      ObjectMapper mapper,
      org.springframework.core.env.Environment env) {
    this.importer = new P3Importer(db, transactions, mapper);
    this.env = env;
  }

  @Override
  public void run(String... args) {
    String path = env.getRequiredProperty("p3.import.path");
    boolean dryRun = env.getProperty("p3.import.dry-run", Boolean.class, Boolean.FALSE);
    log.info("P3 importer starting — input={} dryRun={}", path, dryRun);
    P3ImportResult result;
    try {
      result = importer.run(Path.of(path), dryRun);
    } catch (Exception e) {
      log.error("P3 importer failed to read input: {}", e.getMessage(), e);
      exitCode = 2;
      return;
    }
    log.info(
        "P3 importer summary — inserted={} updated={} skipped={} errors={} checksumMatches={}",
        result.totalInserted(),
        result.totalUpdated(),
        result.skipped().values().stream().mapToLong(Long::longValue).sum(),
        result.totalErrors(),
        result.checksum().matches());
    result
        .errors()
        .forEach(
            e ->
                log.warn(
                    "  row error: {}:{} ({}) — {}",
                    e.file(),
                    e.line(),
                    e.messageId(),
                    e.message()));
    if (!result.checksum().matches()) {
      log.warn(
          "  expected={} actual={} diff={}",
          result.checksum().expected(),
          result.checksum().actual(),
          result.checksum().actual().keySet().stream()
              .filter(
                  k ->
                      !result
                          .checksum()
                          .actual()
                          .get(k)
                          .equals(result.checksum().expected().get(k)))
              .toList());
    }
    if (!result.isSuccess()) {
      exitCode = 1;
    }
  }

  @Override
  public int getExitCode() {
    return exitCode;
  }
}
