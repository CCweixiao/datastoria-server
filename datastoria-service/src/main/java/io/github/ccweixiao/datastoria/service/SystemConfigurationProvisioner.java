package io.github.ccweixiao.datastoria.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import io.github.ccweixiao.datastoria.common.domain.ConfigEntry;
import io.github.ccweixiao.datastoria.common.domain.Ulid;
import io.github.ccweixiao.datastoria.dao.repository.ConfigEntryRepository;

/** Materializes editable system defaults in {@code ds_config_entry} for every tenant. */
@Service
public class SystemConfigurationProvisioner {

  public static final String AGENT_KEY = "settings.ai.agent";
  public static final String QUERY_CONTEXT_KEY = "settings.query-context";
  public static final String UI_KEY = "settings.ui";

  private static final Map<String, String> DEFAULTS = defaults();

  private final ConfigEntryRepository repository;

  public SystemConfigurationProvisioner(ConfigEntryRepository repository) {
    this.repository = repository;
  }

  public synchronized void provision(String tenantId) {
    Set<String> existing =
        repository.findEffective(tenantId, "__system-bootstrap__").stream()
            .filter(entry -> "system".equals(entry.scopeType()))
            .map(ConfigEntry::configKey)
            .collect(Collectors.toSet());
    Instant now = Instant.now();
    DEFAULTS.forEach(
        (key, value) -> {
          if (!existing.contains(key)) {
            repository.save(
                new ConfigEntry(
                    Ulid.next(), tenantId, "system", "system", key, value, "1", 0, now, now, null));
          }
        });
  }

  private static Map<String, String> defaults() {
    Map<String, String> defaults = new LinkedHashMap<>();
    defaults.put(
        AGENT_KEY,
        """
        {"mode":"v2","pruneValidateSql":true,"outputReasoning":true,\
        "reasoningLevel":"medium","autoExplainClickHouseErrors":true,\
        "autoExplainBlacklist":["62","194"],"aiResponseLanguage":"en"}
        """
            .replace("\n", "")
            .trim());
    defaults.put(QUERY_CONTEXT_KEY, "{\"max_execution_time\":60}");
    defaults.put(UI_KEY, "{\"theme\":\"dark\",\"language\":\"system\"}");
    return Map.copyOf(defaults);
  }
}
