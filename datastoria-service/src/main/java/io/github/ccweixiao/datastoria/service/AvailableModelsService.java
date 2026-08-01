package io.github.ccweixiao.datastoria.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.ccweixiao.datastoria.common.config.JdbcSchedulerConfig;
import io.github.ccweixiao.datastoria.common.domain.Model;
import io.github.ccweixiao.datastoria.common.domain.ModelProvider;
import io.github.ccweixiao.datastoria.common.dto.AvailableModelsResponse;
import io.github.ccweixiao.datastoria.common.dto.AvailableProviderResponse;
import io.github.ccweixiao.datastoria.common.dto.ModelProps;
import io.github.ccweixiao.datastoria.common.identity.Identity;
import io.github.ccweixiao.datastoria.dao.repository.ModelProviderRepository;
import io.github.ccweixiao.datastoria.dao.repository.ModelRepository;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

/**
 * Reads enabled models from the catalog and maps them to the A12 {@link ModelProps} shape expected
 * by the frontend.
 */
@Service
public class AvailableModelsService {

  private final ModelRepository modelRepo;
  private final ModelProviderRepository providerRepo;
  private final ObjectMapper mapper;
  private final Scheduler jdbcScheduler;

  public AvailableModelsService(
      ModelRepository modelRepo,
      ModelProviderRepository providerRepo,
      ObjectMapper mapper,
      @Qualifier(JdbcSchedulerConfig.JDBC_SCHEDULER) Scheduler jdbcScheduler) {
    this.modelRepo = modelRepo;
    this.providerRepo = providerRepo;
    this.mapper = mapper;
    this.jdbcScheduler = jdbcScheduler;
  }

  public Mono<AvailableModelsResponse> getAvailableModels(Identity identity) {
    return Mono.fromCallable(
            () -> new AvailableModelsResponse(buildSystemModels(identity), List.of(), List.of()))
        .subscribeOn(jdbcScheduler);
  }

  public Mono<List<AvailableProviderResponse>> getAvailableProviders(Identity identity) {
    return Mono.fromCallable(
            () ->
                providerRepo.findSystemProviders(identity.tenantId()).stream()
                    .filter(ModelProvider::enabled)
                    .filter(provider -> !"oauth".equalsIgnoreCase(provider.authType()))
                    .map(AvailableProviderResponse::from)
                    .toList())
        .subscribeOn(jdbcScheduler);
  }

  private List<ModelProps> buildSystemModels(Identity identity) {
    List<ModelProvider> providers =
        providerRepo.findAccessibleProviders(identity.tenantId(), identity.userId());
    return modelRepo.findEnabledAccessible(identity.tenantId(), identity.userId()).stream()
        .filter(
            model ->
                providers.stream()
                    .anyMatch(
                        provider ->
                            provider.id().equals(model.providerId())
                                && provider.enabled()
                                && (model.secretId() != null || provider.secretId() != null)
                                && !"oauth".equalsIgnoreCase(provider.authType())))
        .map(m -> toModelProps(m, providers))
        .toList();
  }

  private ModelProps toModelProps(Model model, List<ModelProvider> providers) {
    JsonNode capabilities = parseObject(model.capabilitiesJson());
    String providerName =
        providers.stream()
            .filter(p -> p.id().equals(model.providerId()))
            .map(ModelProvider::displayName)
            .findFirst()
            .orElse("unknown");
    return new ModelProps(
        providerName,
        model.modelKey(),
        model.description(),
        model.isFree(),
        capabilities.path("autoSelectable").asBoolean(true),
        !model.enabled(),
        stringList(capabilities.path("supportedEndpoints"), List.of("chat")),
        capabilities.path("supportsImageInput").asBoolean(false),
        capabilities.path("supportsTemperature").asBoolean(true),
        capabilities.path("supportsReasoning").asBoolean(false),
        stringList(capabilities.path("reasoningLevels"), List.of()),
        model.ownerUserId() == null ? "system" : "user",
        model.id());
  }

  private JsonNode parseObject(String json) {
    if (json == null || json.isBlank()) {
      return mapper.createObjectNode();
    }
    try {
      JsonNode parsed = mapper.readTree(json);
      return parsed.isObject() ? parsed : mapper.createObjectNode();
    } catch (com.fasterxml.jackson.core.JsonProcessingException ignored) {
      return mapper.createObjectNode();
    }
  }

  private static List<String> stringList(JsonNode node, List<String> fallback) {
    if (!node.isArray()) {
      return fallback;
    }
    return java.util.stream.StreamSupport.stream(node.spliterator(), false)
        .filter(JsonNode::isTextual)
        .map(JsonNode::asText)
        .toList();
  }
}
