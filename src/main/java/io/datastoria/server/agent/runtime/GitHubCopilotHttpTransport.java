package io.datastoria.server.agent.runtime;

import java.util.Map;

import io.agentscope.core.model.transport.HttpRequest;
import io.agentscope.core.model.transport.HttpResponse;
import io.agentscope.core.model.transport.HttpTransport;
import io.agentscope.core.model.transport.HttpTransportException;
import io.agentscope.core.model.transport.HttpTransportFactory;

import reactor.core.publisher.Flux;

/** Adds the public Copilot client-identification headers expected by the GitHub model endpoint. */
final class GitHubCopilotHttpTransport implements HttpTransport {

  private static final Map<String, String> HEADERS =
      Map.of(
          "Copilot-Integration-Id", "vscode-chat",
          "User-Agent", "GitHubCopilotChat/0.26.7",
          "Editor-Version", "vscode/1.104.1",
          "Editor-Plugin-Version", "copilot-chat/0.26.7");

  private final HttpTransport delegate;

  private GitHubCopilotHttpTransport(HttpTransport delegate) {
    this.delegate = delegate;
  }

  static HttpTransport create() {
    return new GitHubCopilotHttpTransport(HttpTransportFactory.getDefault());
  }

  @Override
  public HttpResponse execute(HttpRequest request) throws HttpTransportException {
    return delegate.execute(withHeaders(request));
  }

  @Override
  public Flux<String> stream(HttpRequest request) {
    return delegate.stream(withHeaders(request));
  }

  @Override
  public void close() {
    delegate.close();
  }

  private static HttpRequest withHeaders(HttpRequest request) {
    return HttpRequest.builder()
        .url(request.getUrl())
        .method(request.getMethod())
        .headers(request.getHeaders())
        .headers(HEADERS)
        .body(request.getBody())
        .build();
  }
}
