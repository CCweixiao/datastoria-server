package io.github.ccweixiao.datastoria.controller;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.ccweixiao.datastoria.common.config.JdbcSchedulerConfig;
import io.github.ccweixiao.datastoria.service.RepositoryBrowseService;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

/** Read-only REST wrapper for the browser code viewer; all filesystem access stays in Java. */
@RestController
@RequestMapping("/api/code")
public class RepositoryBrowseController {

  private final RepositoryBrowseService service;
  private final Scheduler blockingScheduler;

  public RepositoryBrowseController(
      RepositoryBrowseService service,
      @Qualifier(JdbcSchedulerConfig.JDBC_SCHEDULER) Scheduler blockingScheduler) {
    this.service = service;
    this.blockingScheduler = blockingScheduler;
  }

  @GetMapping("/files")
  public Mono<Map<String, List<String>>> files() {
    return Mono.fromCallable(() -> Map.of("paths", service.listFiles()))
        .subscribeOn(blockingScheduler);
  }

  @GetMapping("/file")
  public Mono<RepositoryBrowseService.FileView> file(
      @RequestParam String path,
      @RequestParam(required = false) Integer startLine,
      @RequestParam(required = false) Integer endLine) {
    return Mono.fromCallable(() -> service.read(path, startLine, endLine))
        .subscribeOn(blockingScheduler);
  }
}
