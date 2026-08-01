package io.github.ccweixiao.datastoria.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.ccweixiao.datastoria.common.dto.ClickHouseConnectionMetadataResponse;
import io.github.ccweixiao.datastoria.common.dto.ClickHouseConnectionRequest;
import io.github.ccweixiao.datastoria.common.dto.ClickHouseConnectionResponse;
import io.github.ccweixiao.datastoria.common.dto.ClickHouseConnectionTestResponse;
import io.github.ccweixiao.datastoria.common.dto.ClickHouseQueryRequest;
import io.github.ccweixiao.datastoria.common.identity.AdminAccess;
import io.github.ccweixiao.datastoria.common.identity.IdentityContext;
import io.github.ccweixiao.datastoria.service.ClickHouseConnectionMetadataService;
import io.github.ccweixiao.datastoria.service.ClickHouseConnectionService;

import jakarta.validation.Valid;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/connections")
public class ClickHouseConnectionController {

  private final ClickHouseConnectionService service;
  private final ClickHouseConnectionMetadataService metadataService;

  public ClickHouseConnectionController(
      ClickHouseConnectionService service, ClickHouseConnectionMetadataService metadataService) {
    this.service = service;
    this.metadataService = metadataService;
  }

  @GetMapping
  public Mono<ResponseEntity<List<ClickHouseConnectionResponse>>> list() {
    return IdentityContext.current().flatMap(service::findAll).map(ResponseEntity::ok);
  }

  /** A29 compatibility endpoint. The original backend currently exposes no built-in templates. */
  @GetMapping("/templates")
  public Mono<ResponseEntity<List<Object>>> templates() {
    return Mono.just(ResponseEntity.ok(List.of()));
  }

  @PostMapping
  @AdminAccess
  public Mono<ResponseEntity<ClickHouseConnectionResponse>> create(
      @RequestBody @Valid ClickHouseConnectionRequest request) {
    return IdentityContext.current()
        .flatMap(identity -> service.create(request, identity))
        .map(this::etag);
  }

  @GetMapping("/{id}")
  public Mono<ResponseEntity<ClickHouseConnectionResponse>> get(@PathVariable String id) {
    return IdentityContext.current()
        .flatMap(identity -> service.findById(id, identity))
        .map(this::etag);
  }

  @GetMapping("/{id}/metadata")
  public Mono<ResponseEntity<ClickHouseConnectionMetadataResponse>> metadata(
      @PathVariable String id) {
    return IdentityContext.current()
        .flatMap(identity -> metadataService.get(id, identity))
        .map(ResponseEntity::ok);
  }

  @PutMapping("/{id}")
  @AdminAccess
  public Mono<ResponseEntity<ClickHouseConnectionResponse>> update(
      @PathVariable String id,
      @RequestHeader(value = "If-Match", required = false) String ifMatchHeader,
      @RequestBody @Valid ClickHouseConnectionRequest request) {
    Long ifMatch = RevisionHeader.parse(ifMatchHeader);
    return IdentityContext.current()
        .flatMap(identity -> service.update(id, ifMatch, request, identity))
        .doOnSuccess(ignored -> metadataService.invalidate(id))
        .map(this::etag);
  }

  @DeleteMapping("/{id}")
  @AdminAccess
  public Mono<ResponseEntity<Void>> delete(
      @PathVariable String id,
      @RequestHeader(value = "If-Match", required = false) String ifMatchHeader) {
    Long ifMatch = RevisionHeader.parse(ifMatchHeader);
    return IdentityContext.current()
        .flatMap(identity -> service.delete(id, ifMatch, identity))
        .doOnSuccess(ignored -> metadataService.invalidate(id))
        .thenReturn(ResponseEntity.noContent().build());
  }

  @PostMapping("/{id}:test")
  public Mono<ResponseEntity<ClickHouseConnectionTestResponse>> test(@PathVariable String id) {
    return IdentityContext.current()
        .flatMap(identity -> service.test(id, identity))
        .map(ResponseEntity::ok);
  }

  @PostMapping("/test")
  @AdminAccess
  public Mono<ResponseEntity<ClickHouseConnectionTestResponse>> testTransient(
      @RequestBody @Valid ClickHouseConnectionRequest request) {
    return IdentityContext.current()
        .flatMap(identity -> service.test(request, identity))
        .map(ResponseEntity::ok);
  }

  @PostMapping(value = "/{id}/query", produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<Flux<DataBuffer>>> query(
      @PathVariable String id, @RequestBody @Valid ClickHouseQueryRequest request) {
    Map<String, Object> parameters =
        request.parameters() == null
            ? new LinkedHashMap<>()
            : new LinkedHashMap<>(request.parameters());
    parameters.putIfAbsent("default_format", "JSON");
    return IdentityContext.current()
        .flatMap(
            identity ->
                service.queryStream(
                    id,
                    request.query(),
                    parameters,
                    request.targetNode(),
                    request.targetUser(),
                    identity))
        .map(
            response -> {
              ResponseEntity.BodyBuilder builder = ResponseEntity.status(response.status());
              copyHeader(response.headers(), builder, HttpHeaders.CONTENT_TYPE);
              response
                  .headers()
                  .forEach(
                      (name, values) -> {
                        if (name.toLowerCase(java.util.Locale.ROOT).startsWith("x-clickhouse-")) {
                          values.forEach(value -> builder.header(name, value));
                        }
                      });
              return builder.body(response.body());
            });
  }

  private static void copyHeader(
      HttpHeaders source, ResponseEntity.BodyBuilder target, String headerName) {
    List<String> values = source.get(headerName);
    if (values != null) {
      values.forEach(value -> target.header(headerName, value));
    }
  }

  private ResponseEntity<ClickHouseConnectionResponse> etag(ClickHouseConnectionResponse body) {
    return ResponseEntity.ok().eTag(String.valueOf(body.revision())).body(body);
  }
}
