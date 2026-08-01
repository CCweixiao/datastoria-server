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
                repository.findAll(identity.tenantId(), identity.userId()).stream()
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
    return Mono.fromCallable(
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
                      password.cipher(),
                      password.nonce(),
                      password.keyVersion(),
                      password.maskedHint(),
                      request.enabled() == null || request.enabled(),
                      0,
                      null,
                      null,
                      null);
              return ClickHouseConnectionResponse.from(repository.save(connection));
            })
        .subscribeOn(jdbcScheduler);
  }

  public Mono<ClickHouseConnectionResponse> update(
      String id, Long ifMatch, ClickHouseConnectionRequest request, Identity identity) {
    return Mono.fromCallable(
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
                      password.cipher(),
                      password.nonce(),
                      password.keyVersion(),
                      password.maskedHint(),
                      request.enabled() == null ? existing.enabled() : request.enabled(),
                      existing.revision(),
                      existing.createdAt(),
                      null,
                      null);
              return ClickHouseConnectionResponse.from(
                  repository.update(
                      updated, ifMatch == null ? existing.revision() : ifMatch.longValue()));
            })
        .subscribeOn(jdbcScheduler);
  }

  public Mono<Void> delete(String id, Long ifMatch, Identity identity) {
    return Mono.<Void>fromRunnable(
            () -> {
              ClickHouseConnection existing = require(id, identity);
              repository.softDelete(
                  id,
                  identity.tenantId(),
                  identity.userId(),
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
              return remoteClient
                  .execute(
                      transientConnection,
                      request.password() == null ? "" : request.password(),
                      "SELECT 1 FORMAT JSON")
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
                      : sqlClassifier.requireReadOnly(sql, connection.cluster());
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
    String node = targetNode.trim();
    if (!node.matches("[A-Za-z0-9._:-]+")) {
      throw new IllegalArgumentException("Invalid ClickHouse target node");
    }
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

  private ClickHouseConnection require(String id, Identity identity) {
    return repository
        .findById(id, identity.tenantId(), identity.userId())
        .orElseThrow(() -> new NotFoundException("ClickHouseConnection", id));
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
