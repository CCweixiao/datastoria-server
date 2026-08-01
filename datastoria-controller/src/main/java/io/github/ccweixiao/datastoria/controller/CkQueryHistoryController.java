package io.github.ccweixiao.datastoria.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.ccweixiao.datastoria.common.dto.CkQueryHistoryRequest;
import io.github.ccweixiao.datastoria.common.dto.CkQueryHistoryResponse;
import io.github.ccweixiao.datastoria.common.identity.IdentityContext;
import io.github.ccweixiao.datastoria.service.CkQueryHistoryService;

import jakarta.validation.Valid;
import reactor.core.publisher.Mono;

/**
 * ClickHouse SQL query history for the current user. Listing is always scoped by {@code
 * connectionId} (the cluster connection) + the JWT identity (tenant, user) — the first-level filter
 * — then ordered time-desc; an optional {@code keyword} narrows by raw SQL.
 */
@RestController
@RequestMapping("/api/me/query-history")
public class CkQueryHistoryController {

  private final CkQueryHistoryService service;

  public CkQueryHistoryController(CkQueryHistoryService service) {
    this.service = service;
  }

  @GetMapping
  public Mono<ResponseEntity<List<CkQueryHistoryResponse>>> list(
      @RequestParam String connectionId,
      @RequestParam(value = "keyword", required = false) String keyword) {
    return IdentityContext.current()
        .flatMap(identity -> service.list(connectionId, keyword, identity))
        .map(ResponseEntity::ok);
  }

  @PostMapping
  public Mono<ResponseEntity<CkQueryHistoryResponse>> add(
      @RequestBody @Valid CkQueryHistoryRequest request) {
    return IdentityContext.current()
        .flatMap(identity -> service.add(request, identity))
        .map(ResponseEntity::ok);
  }

  @DeleteMapping("/{id}")
  public Mono<ResponseEntity<Void>> delete(@PathVariable String id) {
    return IdentityContext.current()
        .flatMap(identity -> service.delete(id, identity))
        .thenReturn(ResponseEntity.noContent().build());
  }

  @DeleteMapping
  public Mono<ResponseEntity<Void>> clear(@RequestParam String connectionId) {
    return IdentityContext.current()
        .flatMap(identity -> service.clear(connectionId, identity))
        .thenReturn(ResponseEntity.noContent().build());
  }
}
