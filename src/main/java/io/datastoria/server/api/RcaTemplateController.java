package io.datastoria.server.api;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.datastoria.server.service.RcaTemplateCatalog;

@RestController
@RequestMapping("/api/ai/rca/templates")
public class RcaTemplateController {

  private final RcaTemplateCatalog catalog;

  public RcaTemplateController(RcaTemplateCatalog catalog) {
    this.catalog = catalog;
  }

  @GetMapping
  public Map<String, Object> list() {
    return Map.of("templates", catalog.enabledSources());
  }
}
