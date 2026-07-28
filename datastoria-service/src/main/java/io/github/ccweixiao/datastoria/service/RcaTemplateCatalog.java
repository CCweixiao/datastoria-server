package io.github.ccweixiao.datastoria.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import io.github.ccweixiao.datastoria.dao.persistence.entity.RcaTemplateEntity;
import io.github.ccweixiao.datastoria.dao.persistence.mapper.RcaTemplateMapper;

/** Read-only access to enabled, revisioned RCA templates persisted by the bootstrap. */
@Service
public class RcaTemplateCatalog {

  private final RcaTemplateMapper mapper;

  public RcaTemplateCatalog(RcaTemplateMapper mapper) {
    this.mapper = mapper;
  }

  public Map<String, String> enabledSources() {
    Map<String, String> templates = new LinkedHashMap<>();
    for (RcaTemplateEntity row : mapper.findEnabledSources()) {
      templates.put(row.getTemplateKey(), row.getSourceYaml());
    }
    return templates;
  }

  public Optional<TemplateSnapshot> findEnabled(String key) {
    RcaTemplateEntity row = mapper.findEnabledByKey(key);
    if (row == null) {
      return Optional.empty();
    }
    return Optional.of(
        new TemplateSnapshot(row.getTemplateKey(), row.getRevision(), sha256(row.getSourceYaml())));
  }

  public TemplateSnapshot requireEnabled(String key) {
    return findEnabled(key)
        .orElseThrow(() -> new IllegalStateException("Enabled RCA template not found: " + key));
  }

  private static String sha256(String value) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      return java.util.HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException error) {
      throw new IllegalStateException("SHA-256 unavailable", error);
    }
  }

  public record TemplateSnapshot(String key, long revision, String checksum) {}
}
