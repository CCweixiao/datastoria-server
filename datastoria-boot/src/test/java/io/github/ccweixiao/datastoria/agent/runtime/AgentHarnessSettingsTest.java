package io.github.ccweixiao.datastoria.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class AgentHarnessSettingsTest {

  @Test
  void runtimeOverridesReplaceOnlyProvidedKnobs() {
    AgentHarnessSettings base = AgentHarnessSettings.defaults();

    AgentHarnessSettings overridden = base.withRuntimeOverrides(40, 8_192, 0.5, 200_000);

    assertThat(overridden.maxIters()).isEqualTo(40);
    assertThat(overridden.toolResultEvictionChars()).isEqualTo(8_192);
    assertThat(overridden.compactionTriggerRatio()).isEqualTo(0.5);
    assertThat(overridden.compactionFallbackContextTokens()).isEqualTo(200_000);
    // Process-level knobs stay untouched.
    assertThat(overridden.shutdownTimeoutSeconds()).isEqualTo(base.shutdownTimeoutSeconds());
    assertThat(overridden.dataDir()).isEqualTo(base.dataDir());
  }

  @Test
  void partialOverridesKeepOtherDefaults() {
    AgentHarnessSettings overridden =
        AgentHarnessSettings.defaults().withRuntimeOverrides(40, null, null, null);

    assertThat(overridden.maxIters()).isEqualTo(40);
    assertThat(overridden.toolResultEvictionChars()).isEqualTo(32_768);
    assertThat(overridden.compactionTriggerRatio()).isEqualTo(0.8);
    assertThat(overridden.compactionFallbackContextTokens()).isEqualTo(100_000);
  }

  @Test
  void constructorClampsUnusableValues() {
    AgentHarnessSettings clamped = new AgentHarnessSettings(null, 500, 10, 5.0, 100, 0);

    assertThat(clamped.dataDir())
        .isEqualTo(Path.of(System.getProperty("user.home"), ".datastoria.agent"));
    assertThat(clamped.maxIters()).isEqualTo(100);
    assertThat(clamped.toolResultEvictionChars()).isEqualTo(2_048);
    assertThat(clamped.compactionTriggerRatio()).isEqualTo(0.95);
    assertThat(clamped.compactionFallbackContextTokens()).isEqualTo(8_192);
    assertThat(clamped.shutdownTimeoutSeconds()).isEqualTo(1);
  }

  @Test
  void compactionTriggerScalesWithTheModelContextWindow() {
    AgentRuntimeConfig config = AgentRuntimeConfig.minimal("sys");

    assertThat(config.compactionTriggerTokens(200_000)).isEqualTo(160_000);
    assertThat(config.compactionTriggerTokens(1_000_000)).isEqualTo(800_000);
    assertThat(config.compactionTriggerTokens(0)).isEqualTo(80_000);
    assertThat(config.compactionTriggerTokens(-1)).isEqualTo(80_000);
  }
}
