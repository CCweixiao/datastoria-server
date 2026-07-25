package io.datastoria.server.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.datastoria.server.api.error.NotFoundException;
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
  private final ModelCatalogProvisioner catalogProvisioner;
  private final OAuthCredentialService oauthCredentials;
  private final ObjectMapper mapper;
  private final Scheduler jdbcScheduler;

  public AvailableModelsService(
      ModelRepository modelRepo,
      ModelProviderRepository providerRepo,
      ModelCatalogProvisioner catalogProvisioner,
      OAuthCredentialService oauthCredentials,
      ObjectMapper mapper,
      @Qualifier(JdbcSchedulerConfig.JDBC_SCHEDULER) Scheduler jdbcScheduler) {
    this.modelRepo = modelRepo;
    this.providerRepo = providerRepo;
    this.catalogProvisioner = catalogProvisioner;
    this.oauthCredentials = oauthCredentials;
    this.mapper = mapper;
    this.jdbcScheduler = jdbcScheduler;
  }

  public Mono<AvailableModelsResponse> getAvailableModels(Identity identity) {
    Mono<List<ModelProps>> system =
        Mono.fromCallable(() -> buildSystemModels(identity)).subscribeOn(jdbcScheduler);
    Mono<List<ModelProps>> github =
        oauthCredentials
            .githubModels(identity)
            .map(this::githubModels)
            .onErrorResume(NotFoundException.class, ignored -> Mono.just(List.of()));
    return Mono.zip(system, github)
        .map(result -> new AvailableModelsResponse(result.getT1(), result.getT2()));
  }

  private List<ModelProps> githubModels(JsonNode payload) {
    JsonNode models = payload.isArray() ? payload : payload.path("data");
    if (!models.isArray()) {
      return List.of();
    }
    return java.util.stream.StreamSupport.stream(models.spliterator(), false)
        .filter(model -> model.path("model_picker_enabled").asBoolean(true))
        .map(
            model ->
                new ModelProps(
                    "GitHub Copilot",
                    model.path("id").asText(),
                    githubDescription(model),
                    false,
                    true,
                    false,
                    stringList(model.path("supported_endpoints"), List.of("chat")),
                    model.path("capabilities").path("supports").path("vision").asBoolean(false),
                    true,
                    false,
                    List.of(),
                    "user",
                    null))
        .filter(model -> !model.modelId().isBlank())
        .sorted(java.util.Comparator.comparing(ModelProps::modelId))
        .toList();
  }

  private static String githubDescription(JsonNode model) {
    String name = model.path("name").asText();
    String vendor = model.path("vendor").asText();
    if (!name.isBlank() && !vendor.isBlank()) {
      return "- **Vendor**: " + vendor + "\n\n- **Model**: " + name;
    }
    return !name.isBlank() ? name : model.path("id").asText();
  }

  private List<ModelProps> buildSystemModels(Identity identity) {
    catalogProvisioner.provision(identity);
    List<ModelProvider> providers = providerRepo.findAll(identity.tenantId());
    return modelRepo.findEnabled(identity.tenantId()).stream()
        .map(m -> toModelProps(m, providers))
        .toList();
  }

  private ModelProps toModelProps(Model model, List<ModelProvider> providers) {
    JsonNode capabilities = parseObject(model.capabilitiesJson());
    String providerKey =
        providers.stream()
            .filter(p -> p.id().equals(model.providerId()))
            .map(ModelProvider::providerKey)
            .findFirst()
            .orElse("unknown");
    return new ModelProps(
        providerKey,
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
