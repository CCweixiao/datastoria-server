package io.datastoria.server.service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.datastoria.server.api.error.NotFoundException;
import io.datastoria.server.config.JdbcSchedulerConfig;
import io.datastoria.server.domain.Model;
import io.datastoria.server.domain.ModelProvider;
import io.datastoria.server.domain.Ulid;
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
            .flatMap(
                payload ->
                    Mono.fromCallable(() -> githubModels(identity, payload))
                        .subscribeOn(jdbcScheduler))
            .onErrorResume(NotFoundException.class, ignored -> Mono.just(List.of()));
    return Mono.zip(system, github)
        .map(result -> new AvailableModelsResponse(result.getT1(), result.getT2()));
  }

  private List<ModelProps> githubModels(Identity identity, JsonNode payload) {
    JsonNode models = payload.isArray() ? payload : payload.path("data");
    if (!models.isArray()) {
      return List.of();
    }
    ModelProvider provider = githubProvider(identity);
    Map<String, Model> stored =
        modelRepo.findAll(identity.tenantId()).stream()
            .filter(model -> provider.id().equals(model.providerId()))
            .collect(
                java.util.stream.Collectors.toMap(
                    Model::modelKey, model -> model, (left, right) -> left));
    return java.util.stream.StreamSupport.stream(models.spliterator(), false)
        .filter(model -> model.path("model_picker_enabled").asBoolean(true))
        .map(
            node -> {
              String modelKey = node.path("id").asText();
              Model model =
                  stored.computeIfAbsent(
                      modelKey, ignored -> modelRepo.save(githubModel(identity, provider, node)));
              return new ModelProps(
                  "GitHub Copilot",
                  modelKey,
                  githubDescription(node),
                  isFreeGitHubModel(modelKey),
                  true,
                  !model.enabled(),
                  stringList(node.path("supported_endpoints"), List.of("chat")),
                  supportsVision(node, modelKey),
                  true,
                  supportsReasoning(modelKey),
                  supportsReasoning(modelKey) ? List.of("low", "medium", "high") : List.of(),
                  "user",
                  model.id());
            })
        .filter(model -> !model.modelId().isBlank())
        .sorted(java.util.Comparator.comparing(ModelProps::modelId))
        .toList();
  }

  private ModelProvider githubProvider(Identity identity) {
    return providerRepo.findAll(identity.tenantId()).stream()
        .filter(provider -> "github-copilot".equalsIgnoreCase(provider.providerKey()))
        .findFirst()
        .orElseGet(
            () ->
                providerRepo.save(
                    new ModelProvider(
                        Ulid.next(),
                        identity.tenantId(),
                        "github-copilot",
                        "GitHub Copilot",
                        "https://api.githubcopilot.com",
                        "oauth",
                        true,
                        "{}",
                        null,
                        0,
                        "system",
                        "system",
                        Instant.now(),
                        Instant.now(),
                        null)));
  }

  private Model githubModel(Identity identity, ModelProvider provider, JsonNode node) {
    String modelKey = node.path("id").asText();
    boolean reasoning = supportsReasoning(modelKey);
    com.fasterxml.jackson.databind.node.ObjectNode capabilityNode = mapper.createObjectNode();
    capabilityNode
        .put("autoSelectable", true)
        .put("supportsImageInput", supportsVision(node, modelKey))
        .put("supportsTemperature", true)
        .put("supportsReasoning", reasoning);
    capabilityNode.set(
        "supportedEndpoints",
        mapper.valueToTree(stringList(node.path("supported_endpoints"), List.of("chat"))));
    capabilityNode.set(
        "reasoningLevels",
        mapper.valueToTree(reasoning ? List.of("low", "medium", "high") : List.of()));
    String capabilities = capabilityNode.toString();
    Instant now = Instant.now();
    return new Model(
        Ulid.next(),
        identity.tenantId(),
        provider.id(),
        modelKey,
        node.path("name").asText(modelKey),
        githubDescription(node),
        "discovered",
        true,
        isFreeGitHubModel(modelKey),
        capabilities,
        "{}",
        null,
        0,
        now,
        now,
        null);
  }

  private static boolean supportsVision(JsonNode model, String modelKey) {
    if (model.path("capabilities").path("supports").path("vision").asBoolean(false)) {
      return true;
    }
    String normalized = modelKey.toLowerCase(Locale.ROOT);
    return normalized.contains("gpt-4o")
        || normalized.contains("gpt-5")
        || normalized.contains("claude")
        || normalized.contains("gemini");
  }

  private static boolean supportsReasoning(String modelKey) {
    String normalized = modelKey.toLowerCase(Locale.ROOT);
    return normalized.contains("gpt-5")
        || normalized.contains("o1")
        || normalized.contains("o3")
        || normalized.contains("o4")
        || normalized.contains("claude")
        || normalized.contains("gemini");
  }

  private static boolean isFreeGitHubModel(String modelKey) {
    return java.util.Set.of("gpt-4.1", "gpt-4o", "gpt-5-mini")
        .contains(modelKey.toLowerCase(Locale.ROOT));
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
    Set<String> oauthProviderIds =
        providers.stream()
            .filter(provider -> "oauth".equalsIgnoreCase(provider.authType()))
            .map(ModelProvider::id)
            .collect(java.util.stream.Collectors.toSet());
    return modelRepo.findEnabled(identity.tenantId()).stream()
        // User-scoped OAuth models are returned in their dedicated response collection. Keeping
        // them out of systemModels avoids exposing a model to users who do not own the credential
        // and prevents duplicates after a discovered model has been materialized.
        .filter(model -> !oauthProviderIds.contains(model.providerId()))
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
