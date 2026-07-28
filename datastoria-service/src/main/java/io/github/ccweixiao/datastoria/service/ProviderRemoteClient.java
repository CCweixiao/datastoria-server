package io.github.ccweixiao.datastoria.service;

import java.util.List;

import io.github.ccweixiao.datastoria.common.domain.ModelProvider;
import io.github.ccweixiao.datastoria.common.dto.DiscoveredModelResponse;

public interface ProviderRemoteClient {

  boolean supports(String providerKey);

  List<DiscoveredModelResponse> discoverModels(ModelProvider provider, String credential);
}
