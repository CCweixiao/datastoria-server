package io.datastoria.server.agent.application;

/** Completed persisted tool interaction reconstructed for a later AgentScope model call. */
public record ChatToolExchange(
    String toolCallId, String toolName, String inputJson, String outputJson, boolean error) {}
