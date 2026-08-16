package io.github.ccweixiao.datastoria.common.dto;

/**
 * Admin view of the agent harness runtime settings: the process defaults from {@code
 * datastoria.agent.*}, the stored tenant overrides ({@code null} = not overridden), the merged
 * effective values applied to runs, and the stored entry revision for optimistic locking.
 */
public record AgentHarnessSettingsResponse(
    AgentHarnessSettingsRequest defaults,
    AgentHarnessSettingsRequest overrides,
    AgentHarnessSettingsRequest effective,
    long revision) {}
