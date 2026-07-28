package io.github.ccweixiao.datastoria.service;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.util.UriComponentsBuilder;

import io.github.ccweixiao.datastoria.common.domain.ClickHouseConnection;
import io.github.ccweixiao.datastoria.common.error.ProviderOperationException;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class ClickHouseRemoteClient {

  private final WebClient webClient;

  public ClickHouseRemoteClient(WebClient.Builder builder) {
    this.webClient = builder.build();
  }

  public Mono<String> execute(ClickHouseConnection connection, String password, String query) {
    return execute(connection, password, query, Map.of());
  }

  public Mono<String> execute(
      ClickHouseConnection connection,
      String password,
      String query,
      Map<String, Object> parameters) {
    return request(connection, password, query, parameters)
        .retrieve()
        .onStatus(
            status -> status.isError(),
            response ->
                response
                    .bodyToMono(String.class)
                    .defaultIfEmpty("ClickHouse request failed")
                    .map(
                        body ->
                            new ProviderOperationException(
                                "CLICKHOUSE_QUERY_FAILED", response.statusCode().value(), body)))
        .bodyToMono(String.class)
        .transform(this::mapRequestErrors);
  }

  public Mono<RemoteQueryResponse> executeStream(
      ClickHouseConnection connection,
      String password,
      String query,
      Map<String, Object> parameters) {
    return request(connection, password, query, parameters)
        .retrieve()
        .onStatus(
            status -> status.isError(),
            response ->
                response
                    .bodyToMono(String.class)
                    .defaultIfEmpty("ClickHouse request failed")
                    .map(
                        body ->
                            new ProviderOperationException(
                                "CLICKHOUSE_QUERY_FAILED", response.statusCode().value(), body)))
        .toEntityFlux(DataBuffer.class)
        .map(
            response ->
                new RemoteQueryResponse(
                    response.getStatusCode(),
                    HttpHeaders.readOnlyHttpHeaders(response.getHeaders()),
                    response.getBody().transform(this::mapStreamErrors)))
        .transform(this::mapRequestErrors);
  }

  private WebClient.RequestHeadersSpec<?> request(
      ClickHouseConnection connection,
      String password,
      String query,
      Map<String, Object> parameters) {
    UriComponentsBuilder endpointBuilder =
        UriComponentsBuilder.fromUri(URI.create(connection.url()));
    if (parameters != null) {
      parameters.forEach(
          (key, value) -> {
            if (value != null) {
              endpointBuilder.queryParam(key, value);
            }
          });
    }
    URI endpoint = endpointBuilder.build(true).toUri();
    String credentials = connection.username() + ":" + password;
    String authorization =
        "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    return webClient
        .post()
        .uri(endpoint)
        .header(HttpHeaders.AUTHORIZATION, authorization)
        .contentType(MediaType.TEXT_PLAIN)
        .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN)
        .bodyValue(query);
  }

  private <T> Mono<T> mapRequestErrors(Mono<T> publisher) {
    return publisher
        .onErrorMap(
            WebClientRequestException.class,
            error ->
                new ProviderOperationException(
                    "CLICKHOUSE_UNAVAILABLE",
                    502,
                    "Unable to reach the configured ClickHouse server"))
        .timeout(Duration.ofSeconds(60))
        .onErrorMap(
            TimeoutException.class,
            error ->
                new ProviderOperationException(
                    "CLICKHOUSE_TIMEOUT", 504, "ClickHouse query timed out"));
  }

  private <T> Flux<T> mapStreamErrors(Flux<T> publisher) {
    return publisher
        .onErrorMap(
            WebClientRequestException.class,
            error ->
                new ProviderOperationException(
                    "CLICKHOUSE_UNAVAILABLE",
                    502,
                    "Unable to reach the configured ClickHouse server"))
        .timeout(Duration.ofSeconds(60))
        .onErrorMap(
            TimeoutException.class,
            error ->
                new ProviderOperationException(
                    "CLICKHOUSE_TIMEOUT", 504, "ClickHouse query timed out"));
  }

  public record RemoteQueryResponse(
      HttpStatusCode status, HttpHeaders headers, Flux<DataBuffer> body) {}
}
