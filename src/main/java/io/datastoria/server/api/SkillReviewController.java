package io.datastoria.server.api;

import java.time.Instant;
import java.util.UUID;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.datastoria.server.agent.domain.AgentRunEvent;
import io.datastoria.server.agent.domain.RunContext;
import io.datastoria.server.agent.runtime.AgentRuntimeConfig;
import io.datastoria.server.agent.runtime.HarnessAgentFactory;
import io.datastoria.server.agent.runtime.ModelAdapterProvider;
import io.datastoria.server.agent.runtime.RunnableAgent;
import io.datastoria.server.api.error.ClientSecretNotAllowedException;
import io.datastoria.server.api.error.PlainTextException;
import io.datastoria.server.domain.Model;
import io.datastoria.server.identity.IdentityContext;
import io.datastoria.server.repository.ModelRepository;
import io.datastoria.server.repository.UserModelPreferenceRepository;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/ai/skills/actions/review")
public class SkillReviewController {

  private final UserModelPreferenceRepository preferences;
  private final ModelRepository models;
  private final ModelAdapterProvider adapters;
  private final ObjectMapper mapper;

  public SkillReviewController(
      UserModelPreferenceRepository preferences,
      ModelRepository models,
      ModelAdapterProvider adapters,
      ObjectMapper mapper) {
    this.preferences = preferences;
    this.models = models;
    this.adapters = adapters;
    this.mapper = mapper;
  }

  @PostMapping
  public Mono<JsonNode> review(@RequestBody JsonNode request) {
    if (containsSecret(request)) {
      throw new ClientSecretNotAllowedException("apiKey");
    }
    return IdentityContext.current()
        .flatMap(
            identity -> {
              JsonNode target = request.path("target");
              JsonNode files = target.path("files");
              if (!"file".equals(request.path("scope").asText())
                  || !files.isArray()
                  || files.size() != 1) {
                throw PlainTextException.badRequest("File review requires exactly one target file");
              }
              String path = files.get(0).path("path").asText();
              String content = files.get(0).path("content").asText();
              String skillId = request.path("skillId").asText();
              Model model =
                  preferences
                      .findByUser(identity.tenantId(), identity.userId())
                      .flatMap(
                          preference ->
                              models.findById(preference.selectedModelId(), identity.tenantId()))
                      .orElseThrow(
                          () -> PlainTextException.badRequest("Select an enabled model first"));
              if (!model.enabled() || model.deletedAt() != null) {
                throw PlainTextException.badRequest("Select an enabled model first");
              }
              String runId = UUID.randomUUID().toString();
              RunContext context =
                  new RunContext(
                      runId,
                      identity.tenantId(),
                      identity.userId(),
                      "",
                      "",
                      runId,
                      "",
                      model.id(),
                      Instant.now());
              RunnableAgent agent =
                  new HarnessAgentFactory()
                      .create(
                          context,
                          adapters.adapterFor(model),
                          AgentRuntimeConfig.minimal(
                              "Review the supplied AI skill file. Return valid JSON only."),
                          prompt(skillId, path, content));
              return agent
                  .streamEvents()
                  .ofType(AgentRunEvent.TextDelta.class)
                  .map(AgentRunEvent.TextDelta::delta)
                  .reduce(new StringBuilder(), StringBuilder::append)
                  .map(StringBuilder::toString)
                  .map(this::parse)
                  .doFinally(signal -> agent.close());
            });
  }

  private JsonNode parse(String raw) {
    String json = raw.trim().replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
    try {
      JsonNode parsed = mapper.readTree(json);
      if (!parsed.isObject()) {
        throw new IllegalStateException("Model returned a non-object skill review");
      }
      if (!parsed.has("findings")) {
        ((com.fasterxml.jackson.databind.node.ObjectNode) parsed)
            .put("findings", "## Review Notes\n\nNo major issues found in this file.");
      }
      if (!parsed.has("proposals")) {
        ((com.fasterxml.jackson.databind.node.ObjectNode) parsed).putArray("proposals");
      }
      return parsed;
    } catch (Exception exception) {
      throw new IllegalStateException("Model returned an invalid skill review", exception);
    }
  }

  private boolean containsSecret(JsonNode node) {
    if (node == null) {
      return false;
    }
    if (node.isObject()) {
      java.util.Iterator<java.util.Map.Entry<String, JsonNode>> fields = node.fields();
      while (fields.hasNext()) {
        java.util.Map.Entry<String, JsonNode> field = fields.next();
        String key = field.getKey().toLowerCase(java.util.Locale.ROOT);
        if (key.equals("apikey")
            || key.equals("api_key")
            || key.equals("accesstoken")
            || key.equals("refreshtoken")
            || key.equals("secret")) {
          return true;
        }
        if (containsSecret(field.getValue())) {
          return true;
        }
      }
    } else if (node.isArray()) {
      for (JsonNode child : node) {
        if (containsSecret(child)) {
          return true;
        }
      }
    }
    return false;
  }

  private String prompt(String skillId, String path, String content) {
    return """
        Review this AI skill file for clarity, actionability, precision and maintainability.
        Return JSON only with shape:
        {"findings":"markdown","proposals":[{"path":"%s","reason":"string","updatedContent":"full file"}]}
        Use an empty proposals array when no replacement is useful.
        Skill id: %s
        File path: %s
        File content:
        %s
        """
        .formatted(path, skillId, path, content);
  }
}
