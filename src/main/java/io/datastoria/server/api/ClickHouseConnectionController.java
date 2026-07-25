package io.datastoria.server.api;

import java.util.List;

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

import io.datastoria.server.dto.ClickHouseConnectionRequest;
import io.datastoria.server.dto.ClickHouseConnectionResponse;
import io.datastoria.server.dto.ClickHouseConnectionTestResponse;
import io.datastoria.server.dto.ClickHouseQueryRequest;
import io.datastoria.server.identity.IdentityContext;
import io.datastoria.server.service.ClickHouseConnectionService;

import jakarta.validation.Valid;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/connections")
public class ClickHouseConnectionController {

  private final ClickHouseConnectionService service;

  public ClickHouseConnectionController(ClickHouseConnectionService service) {
    this.service = service;
  }

  @GetMapping
  public Mono<ResponseEntity<List<ClickHouseConnectionResponse>>> list() {
    return IdentityContext.current().flatMap(service::findAll).map(ResponseEntity::ok);
  }

  @PostMapping
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

  @PutMapping("/{id}")
  public Mono<ResponseEntity<ClickHouseConnectionResponse>> update(
      @PathVariable String id,
      @RequestHeader(value = "If-Match", required = false) String ifMatchHeader,
      @RequestBody @Valid ClickHouseConnectionRequest request) {
    Long ifMatch = RevisionHeader.parse(ifMatchHeader);
    return IdentityContext.current()
        .flatMap(identity -> service.update(id, ifMatch, request, identity))
        .map(this::etag);
  }

  @DeleteMapping("/{id}")
  public Mono<ResponseEntity<Void>> delete(
      @PathVariable String id,
      @RequestHeader(value = "If-Match", required = false) String ifMatchHeader) {
    Long ifMatch = RevisionHeader.parse(ifMatchHeader);
    return IdentityContext.current()
        .flatMap(identity -> service.delete(id, ifMatch, identity))
        .thenReturn(ResponseEntity.noContent().build());
  }

  @PostMapping("/{id}:test")
  public Mono<ResponseEntity<ClickHouseConnectionTestResponse>> test(@PathVariable String id) {
    return IdentityContext.current()
        .flatMap(identity -> service.test(id, identity))
        .map(ResponseEntity::ok);
  }

  @PostMapping("/test")
  public Mono<ResponseEntity<ClickHouseConnectionTestResponse>> testTransient(
      @RequestBody @Valid ClickHouseConnectionRequest request) {
    return IdentityContext.current()
        .flatMap(identity -> service.test(request, identity))
        .map(ResponseEntity::ok);
  }

  @PostMapping(value = "/{id}/query", produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<Flux<DataBuffer>>> query(
      @PathVariable String id, @RequestBody @Valid ClickHouseQueryRequest request) {
    return IdentityContext.current()
        .flatMap(
            identity -> service.queryStream(id, request.query(), request.parameters(), identity))
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
