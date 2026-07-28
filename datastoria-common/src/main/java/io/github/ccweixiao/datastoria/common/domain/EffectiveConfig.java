package io.github.ccweixiao.datastoria.common.domain;

import java.util.Map;

/**
 * The merged configuration resulting from layering system &lt; tenant &lt; user entries. The {@code
 * revision} is the maximum revision across all contributing entries and is used as the ETag value.
 */
public record EffectiveConfig(Map<String, String> entries, long revision) {}
