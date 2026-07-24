package io.datastoria.server.service;

import java.util.List;

import io.datastoria.server.domain.ModelProvider;
import io.datastoria.server.dto.DiscoveredModelResponse;

public interface ProviderRemoteClient {

  boolean supports(String providerKey);

  List<DiscoveredModelResponse> discoverModels(ModelProvider provider, String credential);
}
