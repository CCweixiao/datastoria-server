package io.github.ccweixiao.datastoria.agent.application;

/** Server-validated media attachment passed from a UI message into AgentScope. */
public record ChatAttachment(String mediaType, String url, String filename) {}
