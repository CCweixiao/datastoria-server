package io.github.ccweixiao.datastoria.agent.runtime;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.agentscope.core.message.Base64Source;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.DataBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ThinkingBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.message.URLSource;
import io.agentscope.core.model.ChatModelBase;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.ChatUsage;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.ToolSchema;

import reactor.core.publisher.Flux;

/**
 * AgentScope model adapter for the ChatGPT Codex Responses API.
 *
 * <p>Codex subscription authentication is not an OpenAI API key and the endpoint does not accept
 * Chat Completions payloads. This adapter therefore owns the Responses message/tool conversion at
 * the Java model boundary. OAuth tokens never leave the server.
 */
public final class CodexResponsesChatModel extends ChatModelBase {

  private static final String DEFAULT_BASE_URL = "https://chatgpt.com/backend-api/codex";

  private final String modelName;
  private final String accessToken;
  private final String accountId;
  private final WebClient webClient;
  private final ObjectMapper mapper;

  public CodexResponsesChatModel(String modelName, String accessToken, ObjectMapper mapper) {
    this(
        modelName,
        accessToken,
        extractAccountId(accessToken, mapper),
        WebClient.builder().baseUrl(DEFAULT_BASE_URL).build(),
        mapper);
  }

  CodexResponsesChatModel(
      String modelName,
      String accessToken,
      String accountId,
      WebClient webClient,
      ObjectMapper mapper) {
    this.modelName = modelName;
    this.accessToken = accessToken;
    this.accountId = accountId;
    this.webClient = webClient;
    this.mapper = mapper;
    setContextWindowSize(200_000);
    setNativeStructuredOutput(false);
  }

  @Override
  public String getModelName() {
    return modelName;
  }

  @Override
  protected Flux<ChatResponse> doStream(
      List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
    Instant startedAt = Instant.now();
    Map<String, Object> body = requestBody(messages, tools, options);
    return webClient
        .post()
        .uri("/responses")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
        .headers(
            headers -> {
              if (accountId != null && !accountId.isBlank()) {
                headers.set("chatgpt-account-id", accountId);
              }
            })
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.TEXT_EVENT_STREAM)
        .bodyValue(body)
        .exchangeToFlux(
            response -> {
              if (response.statusCode().is2xxSuccessful()) {
                return response.bodyToFlux(
                    new ParameterizedTypeReference<ServerSentEvent<String>>() {});
              }
              return response
                  .releaseBody()
                  .thenMany(
                      Flux.error(
                          new IllegalStateException(
                              "Codex provider returned HTTP " + response.statusCode().value())));
            })
        .transform(events -> responseEvents(events, startedAt));
  }

  private Map<String, Object> requestBody(
      List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("model", modelName);
    body.put("input", input(messages));
    body.put("store", false);
    body.put("stream", true);
    body.put("parallel_tool_calls", false);
    if (options != null && options.getMaxCompletionTokens() != null) {
      body.put("max_output_tokens", options.getMaxCompletionTokens());
    } else if (options != null && options.getMaxTokens() != null) {
      body.put("max_output_tokens", options.getMaxTokens());
    }
    if (options != null && options.getReasoningEffort() != null) {
      body.put("reasoning", Map.of("effort", options.getReasoningEffort(), "summary", "auto"));
    }
    if (tools != null && !tools.isEmpty()) {
      body.put(
          "tools",
          tools.stream()
              .map(
                  tool ->
                      Map.of(
                          "type",
                          "function",
                          "name",
                          tool.getName(),
                          "description",
                          tool.getDescription(),
                          "parameters",
                          tool.getParameters(),
                          "strict",
                          Boolean.TRUE.equals(tool.getStrict())))
              .toList());
    }
    return body;
  }

  private List<Map<String, Object>> input(List<Msg> messages) {
    List<Map<String, Object>> input = new ArrayList<>();
    for (Msg message : messages) {
      String role = role(message.getRole());
      List<TextBlock> textBlocks = message.getContentBlocks(TextBlock.class);
      List<DataBlock> dataBlocks = message.getContentBlocks(DataBlock.class);
      if (!textBlocks.isEmpty() || !dataBlocks.isEmpty()) {
        if ("developer".equals(role)) {
          String text =
              textBlocks.stream().map(TextBlock::getText).reduce("", (left, right) -> left + right);
          if (!text.isBlank()) {
            input.add(Map.of("role", role, "content", text));
          }
        } else {
          List<Map<String, Object>> content = new ArrayList<>();
          String textType = "assistant".equals(role) ? "output_text" : "input_text";
          textBlocks.forEach(
              block -> content.add(Map.of("type", textType, "text", block.getText())));
          dataBlocks.forEach(
              block -> content.add(Map.of("type", "input_image", "image_url", imageUrl(block))));
          input.add(Map.of("role", role, "content", content));
        }
      }
      for (ToolUseBlock tool : message.getContentBlocks(ToolUseBlock.class)) {
        input.add(
            Map.of(
                "type",
                "function_call",
                "call_id",
                tool.getId(),
                "name",
                tool.getName(),
                "arguments",
                json(tool.getInput())));
      }
      for (ToolResultBlock result : message.getContentBlocks(ToolResultBlock.class)) {
        input.add(
            Map.of(
                "type",
                "function_call_output",
                "call_id",
                result.getId(),
                "output",
                toolOutput(result)));
      }
    }
    return input;
  }

  private String imageUrl(DataBlock block) {
    if (block.getSource() instanceof URLSource url) {
      return url.getUrl();
    }
    if (block.getSource() instanceof Base64Source base64) {
      return "data:" + base64.getMediaType() + ";base64," + base64.getData();
    }
    throw new IllegalArgumentException("Unsupported AgentScope image source");
  }

  private Flux<ChatResponse> responseEvents(
      Flux<ServerSentEvent<String>> events, Instant startedAt) {
    AtomicReference<String> responseId = new AtomicReference<>("");
    AtomicBoolean hasToolCall = new AtomicBoolean();
    return events.handle(
        (event, sink) -> {
          String data = event.data();
          if (data == null || data.isBlank() || "[DONE]".equals(data)) {
            return;
          }
          JsonNode payload;
          try {
            payload = mapper.readTree(data);
          } catch (Exception exception) {
            sink.error(new IllegalStateException("Codex returned an invalid stream event"));
            return;
          }
          String type = payload.path("type").asText();
          if ("response.created".equals(type)) {
            responseId.set(payload.path("response").path("id").asText());
          } else if ("response.output_text.delta".equals(type)) {
            String delta = payload.path("delta").asText();
            if (!delta.isEmpty()) {
              sink.next(chunk(responseId.get(), List.of(TextBlock.builder().text(delta).build())));
            }
          } else if ("response.reasoning_summary_text.delta".equals(type)) {
            String delta = payload.path("delta").asText();
            if (!delta.isEmpty()) {
              sink.next(
                  chunk(
                      responseId.get(), List.of(ThinkingBlock.builder().thinking(delta).build())));
            }
          } else if ("response.output_item.done".equals(type)
              && "function_call".equals(payload.path("item").path("type").asText())) {
            JsonNode item = payload.path("item");
            hasToolCall.set(true);
            sink.next(
                chunk(
                    responseId.get(),
                    List.of(
                        ToolUseBlock.builder()
                            .id(item.path("call_id").asText(item.path("id").asText()))
                            .name(item.path("name").asText())
                            .input(readMap(item.path("arguments").asText("{}")))
                            .content(item.path("arguments").asText("{}"))
                            .build())));
          } else if ("response.completed".equals(type) || "response.incomplete".equals(type)) {
            JsonNode response = payload.path("response");
            JsonNode usage = response.path("usage");
            ChatUsage chatUsage =
                usage.isObject()
                    ? ChatUsage.builder()
                        .inputTokens(usage.path("input_tokens").asInt())
                        .outputTokens(usage.path("output_tokens").asInt())
                        .cachedTokens(
                            usage.path("input_tokens_details").path("cached_tokens").asInt())
                        .time(Duration.between(startedAt, Instant.now()).toMillis() / 1000.0)
                        .build()
                    : null;
            sink.next(
                ChatResponse.builder()
                    .id(response.path("id").asText(responseId.get()))
                    .content(List.of())
                    .usage(chatUsage)
                    .metadata(Map.of("provider", "openai-codex"))
                    .finishReason(hasToolCall.get() ? "tool_calls" : "stop")
                    .build());
          } else if ("error".equals(type) || "response.failed".equals(type)) {
            sink.error(new IllegalStateException("Codex provider stream failed"));
          }
        });
  }

  private ChatResponse chunk(String responseId, List<ContentBlock> blocks) {
    return ChatResponse.builder()
        .id(responseId)
        .content(blocks)
        .metadata(Map.of("provider", "openai-codex"))
        .build();
  }

  private String toolOutput(ToolResultBlock result) {
    return result.getOutput().stream()
        .map(
            block -> {
              if (block instanceof TextBlock text) {
                return text.getText();
              }
              return json(block);
            })
        .reduce("", (left, right) -> left + right);
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> readMap(String json) {
    try {
      return mapper.readValue(json, Map.class);
    } catch (Exception exception) {
      throw new IllegalStateException("Codex returned invalid tool arguments");
    }
  }

  private String json(Object value) {
    try {
      return mapper.writeValueAsString(value);
    } catch (Exception exception) {
      throw new IllegalArgumentException("AgentScope content is not JSON serializable", exception);
    }
  }

  private static String role(MsgRole role) {
    if (role == MsgRole.SYSTEM) {
      return "developer";
    }
    if (role == MsgRole.ASSISTANT) {
      return "assistant";
    }
    return "user";
  }

  static String extractAccountId(String token, ObjectMapper mapper) {
    try {
      String[] parts = token.split("\\.");
      if (parts.length < 2) {
        return null;
      }
      byte[] decoded = Base64.getUrlDecoder().decode(parts[1]);
      JsonNode payload = mapper.readTree(new String(decoded, StandardCharsets.UTF_8));
      JsonNode auth = payload.path("https://api.openai.com/auth");
      for (String field : List.of("chatgpt_account_id", "account_id")) {
        if (auth.hasNonNull(field)) {
          return auth.path(field).asText();
        }
      }
      if (auth.path("organizations").isArray() && !auth.path("organizations").isEmpty()) {
        String id = auth.path("organizations").get(0).path("id").asText();
        if (!id.isBlank()) {
          return id;
        }
      }
      for (String field : List.of("chatgpt_account_id", "account_id", "sub")) {
        if (payload.hasNonNull(field)) {
          return payload.path(field).asText();
        }
      }
      return null;
    } catch (Exception ignored) {
      return null;
    }
  }
}
