package io.github.ccweixiao.datastoria.service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import io.github.ccweixiao.datastoria.common.config.JdbcSchedulerConfig;
import io.github.ccweixiao.datastoria.common.crypto.EnvelopeEncryptionService;
import io.github.ccweixiao.datastoria.common.domain.ClickHouseConnection;
import io.github.ccweixiao.datastoria.common.dto.ClickHouseConnectionMetadataResponse;
import io.github.ccweixiao.datastoria.common.dto.ClickHouseConnectionMetadataResponse.ClusterNode;
import io.github.ccweixiao.datastoria.common.error.NotFoundException;
import io.github.ccweixiao.datastoria.common.identity.Identity;
import io.github.ccweixiao.datastoria.dao.repository.ClickHouseConnectionRepository;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

@Service
public class ClickHouseConnectionMetadataService {

  private static final String SERVER_QUERY =
      """
      SELECT currentUser(), timezone(), hostName(), FQDN(), version(),
        hasColumnInTable('system', 'functions', 'description'),
        (SELECT count() > 0 FROM system.functions WHERE name = 'formatQuery'),
        hasColumnInTable('system', 'metric_log', 'ProfileEvent_MergeSourceParts'),
        hasColumnInTable('system', 'metric_log', 'ProfileEvent_MutationTotalParts'),
        (SELECT count() > 0 FROM system.columns WHERE database = 'system' AND table = 'query_log' AND name = 'hostname'),
        (SELECT count() > 0 FROM system.columns WHERE database = 'system' AND table = 'opentelemetry_span_log' AND name = 'hostname'),
        (SELECT count() > 0 FROM system.columns WHERE database = 'system' AND table = 'part_log' AND name = 'hostname'),
        (SELECT readonly != 0 FROM system.settings WHERE name = 'skip_unavailable_shards' LIMIT 1)
      FORMAT JSONCompact
      """;
  private static final String TOPOLOGY_QUERY =
      """
      SELECT cluster, host_name, host_address, port, shard_num, replica_num, is_local
      FROM system.clusters ORDER BY cluster, shard_num, replica_num FORMAT JSONCompact
      """;
  private static final String EVENTS_QUERY =
      "SELECT DISTINCT event FROM system.events ORDER BY event FORMAT JSONCompact";

  private final ClickHouseConnectionRepository repository;
  private final EnvelopeEncryptionService crypto;
  private final ClickHouseRemoteClient remoteClient;
  private final ObjectMapper objectMapper;
  private final Scheduler jdbcScheduler;
  private final Cache<String, Mono<ClickHouseConnectionMetadataResponse>> cache;

  public ClickHouseConnectionMetadataService(
      ClickHouseConnectionRepository repository,
      EnvelopeEncryptionService crypto,
      ClickHouseRemoteClient remoteClient,
      ObjectMapper objectMapper,
      @Qualifier(JdbcSchedulerConfig.JDBC_SCHEDULER) Scheduler jdbcScheduler,
      @Value("${datastoria.clickhouse.metadata-cache.ttl:PT5M}") Duration ttl,
      @Value("${datastoria.clickhouse.metadata-cache.maximum-size:1000}") long maximumSize) {
    this.repository = repository;
    this.crypto = crypto;
    this.remoteClient = remoteClient;
    this.objectMapper = objectMapper;
    this.jdbcScheduler = jdbcScheduler;
    this.cache =
        CacheBuilder.newBuilder()
            .expireAfterWrite(Math.max(1, ttl.toMillis()), TimeUnit.MILLISECONDS)
            .maximumSize(Math.max(1, maximumSize))
            .build();
  }

  public Mono<ClickHouseConnectionMetadataResponse> get(String id, Identity identity) {
    return Mono.fromCallable(
            () ->
                repository
                    .findById(id, identity.tenantId())
                    .orElseThrow(() -> new NotFoundException("ClickHouseConnection", id)))
        .subscribeOn(jdbcScheduler)
        .flatMap(connection -> cached(connection));
  }

  public void invalidate(String connectionId) {
    cache.invalidate(connectionId);
  }

  private Mono<ClickHouseConnectionMetadataResponse> cached(ClickHouseConnection connection) {
    Mono<ClickHouseConnectionMetadataResponse> existing = cache.getIfPresent(connection.id());
    if (existing != null) {
      return existing;
    }
    synchronized (cache) {
      existing = cache.getIfPresent(connection.id());
      if (existing != null) {
        return existing;
      }
      Mono<ClickHouseConnectionMetadataResponse> loaded =
          load(connection).doOnError(ignored -> cache.invalidate(connection.id())).cache();
      cache.put(connection.id(), loaded);
      return loaded;
    }
  }

  private Mono<ClickHouseConnectionMetadataResponse> load(ClickHouseConnection connection) {
    String password = decryptPassword(connection);
    Mono<JsonNode> server = executeJson(connection, password, SERVER_QUERY);
    Mono<JsonNode> topology =
        executeJson(connection, password, TOPOLOGY_QUERY)
            .onErrorReturn(objectMapper.createObjectNode());
    Mono<JsonNode> events =
        executeJson(connection, password, EVENTS_QUERY)
            .onErrorReturn(objectMapper.createObjectNode());
    return Mono.zip(server, topology, events)
        .map(tuple -> map(connection, tuple.getT1(), tuple.getT2(), tuple.getT3()));
  }

  private Mono<JsonNode> executeJson(
      ClickHouseConnection connection, String password, String query) {
    return remoteClient
        .execute(connection, password, query)
        .map(
            body -> {
              try {
                return objectMapper.readTree(body);
              } catch (Exception exception) {
                throw new IllegalStateException("Invalid ClickHouse metadata response", exception);
              }
            });
  }

  private ClickHouseConnectionMetadataResponse map(
      ClickHouseConnection connection, JsonNode server, JsonNode topology, JsonNode events) {
    JsonNode row = server.path("data").path(0);
    String hostName = text(row, 2, connection.name());
    String fqdn = text(row, 3, hostName);
    String configuredCluster = blankToNull(connection.cluster());
    Set<String> availableClusters = new LinkedHashSet<>();
    for (JsonNode node : topology.path("data")) {
      String cluster = text(node, 0, null);
      if (cluster != null) availableClusters.add(cluster);
    }
    String detectedCluster =
        configuredCluster != null
            ? configuredCluster
            : availableClusters.size() == 1 ? availableClusters.iterator().next() : null;
    List<ClusterNode> nodes = new ArrayList<>();
    Set<String> hostNames = new LinkedHashSet<>();
    if (detectedCluster != null) {
      for (JsonNode node : topology.path("data")) {
        if (!detectedCluster.equals(text(node, 0, null))) continue;
        String nodeName = text(node, 1, "");
        String address = text(node, 2, nodeName);
        int port = node.path(3).asInt(9000);
        if (!nodeName.isBlank()) hostNames.add(nodeName);
        nodes.add(
            new ClusterNode(
                nodeName,
                formatAddress(address, port),
                node.path(4).asInt(),
                node.path(5).asInt(),
                node.path(6).asBoolean()));
      }
    }
    if (hostNames.isEmpty() && !hostName.isBlank()) hostNames.add(hostName);
    List<String> profileEvents = new ArrayList<>();
    for (JsonNode event : events.path("data")) {
      String value = text(event, 0, null);
      if (value != null) profileEvents.add(value);
    }
    return new ClickHouseConnectionMetadataResponse(
        fqdn,
        detectedCluster == null ? null : fqdn,
        text(row, 4, null),
        text(row, 0, connection.username()),
        text(row, 1, "UTC"),
        bool(row, 5),
        bool(row, 7),
        bool(row, 8),
        bool(row, 9),
        bool(row, 10),
        bool(row, 11),
        bool(row, 6),
        bool(row, 12),
        List.copyOf(hostNames),
        detectedCluster,
        List.copyOf(nodes),
        List.copyOf(profileEvents));
  }

  private String decryptPassword(ClickHouseConnection connection) {
    if (connection.passwordCipher() == null) return "";
    byte[] plaintext =
        crypto.decrypt(
            connection.passwordCipher(),
            connection.passwordNonce(),
            connection.passwordKeyVersion());
    try {
      return new String(plaintext, StandardCharsets.UTF_8);
    } finally {
      Arrays.fill(plaintext, (byte) 0);
    }
  }

  private static String text(JsonNode row, int index, String fallback) {
    JsonNode value = row.path(index);
    return value.isTextual() ? value.asText() : fallback;
  }

  private static boolean bool(JsonNode row, int index) {
    return row.path(index).asBoolean(false);
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private static String formatAddress(String host, int port) {
    String normalized = host == null ? "" : host.trim();
    if (normalized.contains(":")) normalized = "[" + normalized.replaceAll("^\\[|]$", "") + "]";
    return normalized + ":" + port;
  }
}
