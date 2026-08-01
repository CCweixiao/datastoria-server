package io.github.ccweixiao.datastoria.common.dto;

import java.util.List;

public record ClickHouseConnectionMetadataResponse(
    String displayName,
    String remoteHostName,
    String serverVersion,
    String internalUser,
    String timezone,
    boolean functionTableHasDescriptionColumn,
    boolean metricLogTableHasProfileEventMergeSourceParts,
    boolean metricLogTableHasProfileEventMutationTotalParts,
    boolean queryLogTableHasHostnameColumn,
    boolean spanLogTableHasHostnameColumn,
    boolean partLogTableHasNodeNameColumn,
    boolean hasFormatQueryFunction,
    boolean readonlySkipUnavailableShards,
    List<String> hostNames,
    String detectedCluster,
    List<ClusterNode> clusterNodes,
    List<String> profileEvents) {

  public record ClusterNode(
      String hostName, String hostAddress, int shardNumber, int replicaNumber, boolean local) {}
}
