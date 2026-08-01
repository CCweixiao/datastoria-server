package io.github.ccweixiao.datastoria.service;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import io.github.ccweixiao.datastoria.common.clickhouse.ClickHouseReadOnlySqlClassifier;
import io.github.ccweixiao.datastoria.common.config.JdbcSchedulerConfig;
import io.github.ccweixiao.datastoria.common.crypto.EnvelopeEncryptionService;
import io.github.ccweixiao.datastoria.common.crypto.MaskedHintBuilder;
import io.github.ccweixiao.datastoria.common.domain.ClickHouseConnection;
import io.github.ccweixiao.datastoria.common.domain.Ulid;
import io.github.ccweixiao.datastoria.common.dto.ClickHouseConnectionRequest;
import io.github.ccweixiao.datastoria.common.dto.ClickHouseConnectionResponse;
import io.github.ccweixiao.datastoria.common.dto.ClickHouseConnectionTestResponse;
import io.github.ccweixiao.datastoria.common.error.AdminAccessRequiredException;
import io.github.ccweixiao.datastoria.common.error.NotFoundException;
import io.github.ccweixiao.datastoria.common.identity.Identity;
import io.github.ccweixiao.datastoria.dao.repository.ClickHouseConnectionRepository;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

@Service
public class ClickHouseConnectionService {

  private final ClickHouseConnectionRepository repository;
  private final EnvelopeEncryptionService crypto;
  private final ClickHouseRemoteClient remoteClient;
  private final Scheduler jdbcScheduler;
  private final ClickHouseReadOnlySqlClassifier sqlClassifier =
      new ClickHouseReadOnlySqlClassifier();

  public ClickHouseConnectionService(
      ClickHouseConnectionRepository repository,
      EnvelopeEncryptionService crypto,
      ClickHouseRemoteClient remoteClient,
      @Qualifier(JdbcSchedulerConfig.JDBC_SCHEDULER) Scheduler jdbcScheduler) {
    this.repository = repository;
    this.crypto = crypto;
    this.remoteClient = remoteClient;
    this.jdbcScheduler = jdbcScheduler;
  }

  public Mono<List<ClickHouseConnectionResponse>> findAll(Identity identity) {
    return Mono.fromCallable(
            () ->
                repository.findAll(identity.tenantId()).stream()
                    .map(ClickHouseConnectionResponse::from)
                    .toList())
        .subscribeOn(jdbcScheduler);
  }

  public Mono<ClickHouseConnectionResponse> findById(String id, Identity identity) {
    return Mono.fromCallable(() -> ClickHouseConnectionResponse.from(require(id, identity)))
        .subscribeOn(jdbcScheduler);
  }

  public Mono<ClickHouseConnectionResponse> create(
      ClickHouseConnectionRequest request, Identity identity) {
    requireAdmin(identity);
    return Mono.defer(
            () -> {
              validateUrl(request.url());
              EncryptedPassword password = encrypt(request.password());
              ClickHouseConnection connection =
                  new ClickHouseConnection(
                      Ulid.next(),
                      identity.tenantId(),
                      identity.userId(),
                      request.name().trim(),
                      request.url().trim(),
                      request.username().trim(),
                      trimToNull(request.cluster()),
                      trimToNull(request.remark()),
                      password.cipher(),
                      password.nonce(),
                      password.keyVersion(),
                      password.maskedHint(),
                      request.enabled() == null || request.enabled(),
                      0,
                      null,
                      null,
                      null);
              String plaintextPassword = request.password() == null ? "" : request.password();
              return validateConfiguredCluster(connection, plaintextPassword)
                  .then(
                      Mono.fromCallable(
                          () -> ClickHouseConnectionResponse.from(repository.save(connection))));
            })
        .subscribeOn(jdbcScheduler);
  }

  public Mono<ClickHouseConnectionResponse> update(
      String id, Long ifMatch, ClickHouseConnectionRequest request, Identity identity) {
    requireAdmin(identity);
    return Mono.defer(
            () -> {
              validateUrl(request.url());
              ClickHouseConnection existing = require(id, identity);
              EncryptedPassword password =
                  request.password() == null
                      ? new EncryptedPassword(
                          existing.passwordCipher(),
                          existing.passwordNonce(),
                          existing.passwordKeyVersion(),
                          existing.passwordMaskedHint())
                      : encrypt(request.password());
              ClickHouseConnection updated =
                  new ClickHouseConnection(
                      existing.id(),
                      existing.tenantId(),
                      existing.ownerUserId(),
                      request.name().trim(),
                      request.url().trim(),
                      request.username().trim(),
                      trimToNull(request.cluster()),
                      trimToNull(request.remark()),
                      password.cipher(),
                      password.nonce(),
                      password.keyVersion(),
                      password.maskedHint(),
                      request.enabled() == null ? existing.enabled() : request.enabled(),
                      existing.revision(),
                      existing.createdAt(),
                      null,
                      null);
              String plaintextPassword =
                  request.password() == null ? decryptPassword(existing) : request.password();
              return validateConfiguredCluster(updated, plaintextPassword)
                  .then(
                      Mono.fromCallable(
                          () ->
                              ClickHouseConnectionResponse.from(
                                  repository.update(
                                      updated,
                                      ifMatch == null
                                          ? existing.revision()
                                          : ifMatch.longValue()))));
            })
        .subscribeOn(jdbcScheduler);
  }

  public Mono<Void> delete(String id, Long ifMatch, Identity identity) {
    requireAdmin(identity);
    return Mono.<Void>fromRunnable(
            () -> {
              ClickHouseConnection existing = require(id, identity);
              repository.softDelete(
                  id,
                  identity.tenantId(),
                  ifMatch == null ? existing.revision() : ifMatch.longValue());
            })
        .subscribeOn(jdbcScheduler);
  }

  public Mono<ClickHouseConnectionTestResponse> test(String id, Identity identity) {
    return Mono.defer(
            () -> {
              ClickHouseConnection connection = require(id, identity);
              long started = System.nanoTime();
              return remoteClient
                  .execute(connection, decryptPassword(connection), "SELECT 1 FORMAT JSON")
                  .thenReturn(
                      new ClickHouseConnectionTestResponse(
                          true,
                          TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started),
                          "Connection succeeded"));
            })
        .subscribeOn(jdbcScheduler);
  }

  public Mono<ClickHouseConnectionTestResponse> test(
      ClickHouseConnectionRequest request, Identity identity) {
    requireAdmin(identity);
    return Mono.defer(
            () -> {
              validateUrl(request.url());
              ClickHouseConnection transientConnection =
                  new ClickHouseConnection(
                      "transient",
                      identity.tenantId(),
                      identity.userId(),
                      request.name().trim(),
                      request.url().trim(),
                      request.username().trim(),
                      trimToNull(request.cluster()),
                      trimToNull(request.remark()),
                      null,
                      null,
                      null,
                      null,
                      true,
                      0,
                      null,
                      null,
                      null);
              long started = System.nanoTime();
              String plaintextPassword = request.password() == null ? "" : request.password();
              return validateConfiguredCluster(transientConnection, plaintextPassword)
                  .then(
                      Mono.defer(
                          () ->
                              remoteClient.execute(
                                  transientConnection, plaintextPassword, "SELECT 1 FORMAT JSON")))
                  .thenReturn(
                      new ClickHouseConnectionTestResponse(
                          true,
                          TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started),
                          "Connection succeeded"));
            })
        .subscribeOn(jdbcScheduler);
  }

  public Mono<String> query(
      String id, String sql, Map<String, Object> parameters, Identity identity) {
    return Mono.defer(
            () -> {
              ClickHouseConnection connection = require(id, identity);
              if (!connection.enabled()) {
                return Mono.error(
                    new IllegalArgumentException("ClickHouse connection is disabled: " + id));
              }
              return remoteClient.execute(connection, decryptPassword(connection), sql, parameters);
            })
        .subscribeOn(jdbcScheduler);
  }

  public Mono<ClickHouseRemoteClient.RemoteQueryResponse> queryStream(
      String id, String sql, Map<String, Object> parameters, Identity identity) {
    return queryStream(id, sql, parameters, null, null, identity);
  }

  public Mono<ClickHouseRemoteClient.RemoteQueryResponse> queryStream(
      String id,
      String sql,
      Map<String, Object> parameters,
      String targetNode,
      String targetUser,
      Identity identity) {
    return Mono.defer(
            () -> {
              ClickHouseConnection connection = require(id, identity);
              if (!connection.enabled()) {
                return Mono.error(
                    new IllegalArgumentException("ClickHouse connection is disabled: " + id));
              }
              String password = decryptPassword(connection);
              // Non-admin callers may only run read-only SQL; admins may execute DDL/DML.
              String gatedSql =
                  identity.isAdmin()
                      ? sql
                      : sqlClassifier.requireReadOnly(sql, effectiveCluster(connection.cluster()));
              String effectiveSql =
                  wrapForTargetNode(
                      gatedSql, targetNode, targetUser, connection.username(), password);
              return remoteClient.executeStream(connection, password, effectiveSql, parameters);
            })
        .subscribeOn(jdbcScheduler);
  }

  static String wrapForTargetNode(
      String sql,
      String targetNode,
      String targetUser,
      String configuredUser,
      String configuredPassword) {
    if (targetNode == null || targetNode.isBlank()) {
      return sql;
    }
    String node = normalizeTargetNode(targetNode);
    String user = targetUser == null || targetUser.isBlank() ? configuredUser : targetUser.trim();
    return """
        SELECT * FROM remote(
          '%s',
          view(
        %s
          ),
          '%s',
          '%s'
        )
        """
        .formatted(
            escapeClickHouseString(node),
            sql,
            escapeClickHouseString(user),
            escapeClickHouseString(configuredPassword));
  }

  private static String escapeClickHouseString(String value) {
    return value.replace("\\", "\\\\").replace("'", "\\'");
  }

  static String normalizeTargetNode(String targetNode) {
    String node = targetNode.trim();
    if (node.matches("\\[[0-9A-Fa-f:.%]+]:[0-9]{1,5}")) {
      return node;
    }
    if (node.chars().filter(character -> character == ':').count() > 1) {
      int portSeparator = node.lastIndexOf(':');
      String host = node.substring(0, portSeparator);
      String port = node.substring(portSeparator + 1);
      if (host.matches("[0-9A-Fa-f:.%]+") && port.matches("[0-9]{1,5}")) {
        return "[" + host + "]:" + port;
      }
    }
    if (node.matches("[A-Za-z0-9._-]+(?::[0-9]{1,5})?")) {
      return node;
    }
    throw new IllegalArgumentException("Invalid ClickHouse target node");
  }

  private static String effectiveCluster(String configuredCluster) {
    return configuredCluster == null || configuredCluster.isBlank()
        ? "default"
        : configuredCluster.trim();
  }

  private Mono<Void> validateConfiguredCluster(
      ClickHouseConnection connection, String plaintextPassword) {
    String cluster = connection.cluster();
    if (cluster == null || cluster.isBlank()) {
      return Mono.empty();
    }
    String normalizedCluster = cluster.trim();
    String sql =
        "SELECT count() FROM system.clusters WHERE cluster = '"
            + escapeClickHouseString(normalizedCluster)
            + "' FORMAT TabSeparatedRaw";
    return remoteClient
        .execute(connection, plaintextPassword, sql)
        .flatMap(
            result -> {
              try {
                if (Long.parseLong(result.trim()) > 0) {
                  return Mono.empty();
                }
              } catch (NumberFormatException ignored) {
                // Treat unexpected ClickHouse output as a failed validation.
              }
              return Mono.error(
                  new IllegalArgumentException(
                      "ClickHouse cluster is not defined in system.clusters: "
                          + normalizedCluster));
            });
  }

  private ClickHouseConnection require(String id, Identity identity) {
    return repository
        .findById(id, identity.tenantId())
        .orElseThrow(() -> new NotFoundException("ClickHouseConnection", id));
  }

  private static void requireAdmin(Identity identity) {
    if (!identity.isAdmin()) {
      throw new AdminAccessRequiredException();
    }
  }

  private EncryptedPassword encrypt(String plaintext) {
    if (plaintext == null || plaintext.isEmpty()) {
      return new EncryptedPassword(null, null, null, null);
    }
    var encrypted = crypto.encrypt(plaintext.getBytes(StandardCharsets.UTF_8));
    return new EncryptedPassword(
        encrypted.cipherText(),
        encrypted.nonce(),
        encrypted.keyVersion(),
        MaskedHintBuilder.build(plaintext));
  }

  private String decryptPassword(ClickHouseConnection connection) {
    if (connection.passwordCipher() == null) {
      return "";
    }
    byte[] plaintext =
        crypto.decrypt(
            connection.passwordCipher(),
            connection.passwordNonce(),
            connection.passwordKeyVersion());
    try {
      return new String(plaintext, StandardCharsets.UTF_8);
    } finally {
      java.util.Arrays.fill(plaintext, (byte) 0);
    }
  }

  private static void validateUrl(String rawUrl) {
    URI uri = URI.create(rawUrl.trim());
    if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
        || uri.getHost() == null
        || uri.getUserInfo() != null) {
      throw new IllegalArgumentException(
          "ClickHouse URL must be an http(s) URL without credentials");
    }
  }

  private static String trimToNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }

  private record EncryptedPassword(
      byte[] cipher, byte[] nonce, String keyVersion, String maskedHint) {}
}
