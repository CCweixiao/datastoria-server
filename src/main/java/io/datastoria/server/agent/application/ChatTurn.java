package io.datastoria.server.agent.application;

/** AgentScope-free historical chat turn supplied to a new run. */
public record ChatTurn(String role, String text) {}
