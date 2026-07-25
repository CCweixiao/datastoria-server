package io.datastoria.server.agent.runtime;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.GenerateOptions;
import io.datastoria.server.agent.domain.RunContext;

import reactor.core.publisher.Mono;

/** Generates the same optional first-turn session title at the server-owned model boundary. */
@Component
public class ModelTitleGenerator {

  private static final String PROMPT =
      """
      You generate short chat session titles.
      Return JSON with exactly one field: "title".
      The title must be 3 to 10 words and at most 64 characters.
      Use plain words only. Do not include quotes, punctuation, emojis, or explanations.
      """;

  private final ObjectMapper mapper;

  public ModelTitleGenerator(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  public Mono<String> generate(ModelAdapter adapter, RunContext context, String userText) {
    Msg system = Msg.builder().role(MsgRole.SYSTEM).textContent(PROMPT).build();
    Msg user =
        Msg.builder()
            .role(MsgRole.USER)
            .textContent(userText.substring(0, Math.min(userText.length(), 300)))
            .build();
    GenerateOptions options =
        GenerateOptions.builder().stream(true)
            .temperature(0.0)
            .maxCompletionTokens(80)
            .additionalBodyParams(Map.of("store", false))
            .build();
    return adapter.modelFor(context).stream(List.of(system, user), List.of(), options)
        .flatMapIterable(response -> response.getContent())
        .filter(TextBlock.class::isInstance)
        .map(TextBlock.class::cast)
        .map(TextBlock::getText)
        .collect(StringBuilder::new, StringBuilder::append)
        .map(StringBuilder::toString)
        .map(this::parseTitle)
        .filter(title -> !title.isBlank());
  }

  private String parseTitle(String raw) {
    String candidate = raw.trim();
    try {
      candidate = mapper.readTree(candidate).path("title").asText(candidate);
    } catch (Exception ignored) {
      // Providers without structured-output support may return plain title text.
    }
    candidate = candidate.replaceAll("^[\"'`]+|[\"'`]+$", "").trim();
    return candidate.substring(0, Math.min(candidate.length(), 64));
  }
}
