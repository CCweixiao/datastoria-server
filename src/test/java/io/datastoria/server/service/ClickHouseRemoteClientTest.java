package io.datastoria.server.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import io.datastoria.server.domain.ClickHouseConnection;

import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

class ClickHouseRemoteClientTest {

  @Test
  void cancellationReachesNetworkAndCredentialStaysOutOfBody() throws Exception {
    AtomicReference<String> authorization = new AtomicReference<>();
    AtomicReference<String> body = new AtomicReference<>();
    CountDownLatch requestStarted = new CountDownLatch(1);
    CountDownLatch responseCancelled = new CountDownLatch(1);
    DisposableServer server =
        HttpServer.create()
            .host("127.0.0.1")
            .port(0)
            .handle(
                (request, response) -> {
                  authorization.set(request.requestHeaders().get("Authorization"));
                  return request
                      .receive()
                      .aggregate()
                      .asString()
                      .flatMap(
                          query -> {
                            body.set(query);
                            requestStarted.countDown();
                            return response
                                .sendString(
                                    Mono.<String>never().doOnCancel(responseCancelled::countDown))
                                .then();
                          });
                })
            .bindNow();
    try {
      String password = "network-only-secret";
      ClickHouseConnection connection =
          new ClickHouseConnection(
              "connection",
              "tenant",
              "user",
              "local",
              "http://127.0.0.1:" + server.port(),
              "agent",
              null,
              null,
              null,
              null,
              null,
              true,
              0,
              null,
              null,
              null);
      ClickHouseRemoteClient client = new ClickHouseRemoteClient(WebClient.builder());

      Disposable request =
          client.execute(connection, password, "SELECT 1", Map.of()).subscribe(ignored -> {});
      assertThat(requestStarted.await(2, TimeUnit.SECONDS)).isTrue();
      request.dispose();

      assertThat(responseCancelled.await(2, TimeUnit.SECONDS)).isTrue();
      assertThat(body.get()).isEqualTo("SELECT 1").doesNotContain(password);
      assertThat(authorization.get())
          .isEqualTo(
              "Basic "
                  + Base64.getEncoder()
                      .encodeToString(("agent:" + password).getBytes(StandardCharsets.UTF_8)));
    } finally {
      server.disposeNow();
    }
  }
}
