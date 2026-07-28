package io.github.ccweixiao.datastoria.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.agentscope.core.message.Base64Source;
import io.agentscope.core.message.DataBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.model.ToolSchema;

import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

class CodexResponsesChatModelTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void convertsAgentScopeMessagesToolsAndCodexResponse() throws Exception {
    AtomicReference<String> authorization = new AtomicReference<>();
    AtomicReference<String> account = new AtomicReference<>();
    AtomicReference<JsonNode> requestBody = new AtomicReference<>();
    DisposableServer server =
        HttpServer.create()
            .host("127.0.0.1")
            .port(0)
            .handle(
                (request, response) -> {
                  authorization.set(request.requestHeaders().get("Authorization"));
                  account.set(request.requestHeaders().get("chatgpt-account-id"));
                  return request
                      .receive()
                      .aggregate()
                      .asString()
                      .flatMap(
                          body -> {
                            try {
                              requestBody.set(mapper.readTree(body));
                            } catch (Exception exception) {
                              return Mono.error(exception);
                            }
                            return response
                                .status(200)
                                .header("Content-Type", MediaType.TEXT_EVENT_STREAM_VALUE)
                                .sendString(
                                    Mono.just(
                                        """
                                        data: {"type":"response.created","response":{"id":"resp-1"}}

                                        data: {"type":"response.reasoning_summary_text.delta","delta":"checking"}

                                        data: {"type":"response.output_text.delta","delta":"I need a tool"}

                                        data: {"type":"response.output_item.done","item":{"type":"function_call","call_id":"call-1","name":"get_tables","arguments":"{\\"database\\":\\"default\\"}"}}

                                        data: {"type":"response.completed","response":{"id":"resp-1","usage":{"input_tokens":10,"output_tokens":4,"input_tokens_details":{"cached_tokens":3}}}}

                                        data: [DONE]

                                        """))
                                .then();
                          });
                })
            .bindNow();
    try {
      String token = jwt("account-123");
      CodexResponsesChatModel model =
          new CodexResponsesChatModel(
              "gpt-5.4",
              token,
              CodexResponsesChatModel.extractAccountId(token, mapper),
              WebClient.builder().baseUrl("http://127.0.0.1:" + server.port()).build(),
              mapper);
      var responses =
          model.stream(
                  List.of(
                      Msg.builder().role(MsgRole.SYSTEM).textContent("You are helpful").build(),
                      Msg.builder()
                          .role(MsgRole.USER)
                          .content(
                              List.of(
                                  TextBlock.builder().text("List tables").build(),
                                  DataBlock.builder()
                                      .source(
                                          new Base64Source(
                                              "image/png",
                                              Base64.getEncoder()
                                                  .encodeToString(
                                                      "image".getBytes(StandardCharsets.UTF_8))))
                                      .name("schema.png")
                                      .build()))
                          .build()),
                  List.of(
                      ToolSchema.builder()
                          .name("get_tables")
                          .description("Lists tables")
                          .parameters(
                              Map.of(
                                  "type",
                                  "object",
                                  "properties",
                                  Map.of("database", Map.of("type", "string"))))
                          .build()),
                  null)
              .collectList()
              .block();
      var textResponse =
          responses.stream()
              .filter(item -> item.getContent().stream().anyMatch(TextBlock.class::isInstance))
              .findFirst()
              .orElseThrow();
      var toolResponse =
          responses.stream()
              .filter(item -> item.getContent().stream().anyMatch(ToolUseBlock.class::isInstance))
              .findFirst()
              .orElseThrow();
      var finalResponse = responses.get(responses.size() - 1);

      assertThat(authorization.get()).isEqualTo("Bearer " + token);
      assertThat(account.get()).isEqualTo("account-123");
      assertThat(requestBody.get().path("model").asText()).isEqualTo("gpt-5.4");
      assertThat(requestBody.get().path("store").asBoolean()).isFalse();
      assertThat(requestBody.get().path("stream").asBoolean()).isTrue();
      assertThat(requestBody.get().path("input").get(0).path("role").asText())
          .isEqualTo("developer");
      assertThat(
              requestBody
                  .get()
                  .path("input")
                  .get(1)
                  .path("content")
                  .get(1)
                  .path("image_url")
                  .asText())
          .startsWith("data:image/png;base64,");
      assertThat(requestBody.get().path("tools").get(0).path("name").asText())
          .isEqualTo("get_tables");
      assertThat(
              textResponse.getContent().stream()
                  .filter(TextBlock.class::isInstance)
                  .map(TextBlock.class::cast)
                  .findFirst()
                  .orElseThrow()
                  .getText())
          .isEqualTo("I need a tool");
      assertThat(
              toolResponse.getContent().stream()
                  .filter(ToolUseBlock.class::isInstance)
                  .map(ToolUseBlock.class::cast)
                  .findFirst()
                  .orElseThrow()
                  .getInput())
          .containsEntry("database", "default");
      assertThat(finalResponse.getUsage().getCachedTokens()).isEqualTo(3);
      assertThat(finalResponse.getFinishReason()).isEqualTo("tool_calls");
    } finally {
      server.disposeNow();
    }
  }

  private String jwt(String accountId) throws Exception {
    String header =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString("{}".getBytes(StandardCharsets.UTF_8));
    String payload =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(
                mapper.writeValueAsBytes(
                    Map.of(
                        "https://api.openai.com/auth", Map.of("chatgpt_account_id", accountId))));
    return header + "." + payload + ".signature";
  }
}
