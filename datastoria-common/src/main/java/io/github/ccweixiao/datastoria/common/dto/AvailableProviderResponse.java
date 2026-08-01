package io.github.ccweixiao.datastoria.common.dto;

import io.github.ccweixiao.datastoria.common.domain.ModelProvider;

/** Sanitized provider descriptor available to authenticated users creating private models. */
public record AvailableProviderResponse(String id, String providerKey, String displayName) {

  public static AvailableProviderResponse from(ModelProvider provider) {
    return new AvailableProviderResponse(
        provider.id(), provider.providerKey(), provider.displayName());
  }
}
