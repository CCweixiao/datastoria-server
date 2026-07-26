package io.datastoria.server.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.datastoria.server.config.JdbcSchedulerConfig;
import io.datastoria.server.domain.Model;
import io.datastoria.server.domain.ModelProvider;
import io.datastoria.server.dto.AvailableModelsResponse;
import io.datastoria.server.dto.ModelProps;
import io.datastoria.server.identity.Identity;
import io.datastoria.server.repository.ModelProviderRepository;
import io.datastoria.server.repository.ModelRepository;

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

  private List<ModelProps> buildSystemModels(Identity identity) {
    List<ModelProvider> providers = providerRepo.findAll(identity.tenantId());
    return modelRepo.findEnabled(identity.tenantId()).stream()
        .filter(
            model ->
                providers.stream()
                    .anyMatch(
                        provider ->
                            provider.id().equals(model.providerId())
                                && provider.enabled()
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
        model.source(),
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
