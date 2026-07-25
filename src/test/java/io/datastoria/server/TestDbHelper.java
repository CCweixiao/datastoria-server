package io.datastoria.server;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Deletes all rows from ds_* tables so each test starts with a clean database. */
@Component
public class TestDbHelper {

  private static final String[] TABLES = {
    "ds_user_state",
    "ds_agent_run_skill",
    "ds_skill_resource",
    "ds_agent_skill_resource",
    "ds_skill_revision",
    "ds_agent_skill",
    "ds_clickhouse_connection",
    "ds_session_share",
    "ds_feedback_event",
    "ds_chat_message",
    "ds_agent_event",
    "ds_agent_checkpoint",
    "ds_agent_run",
    "ds_chat_session",
    "ds_agent_revision",
    "ds_agent_definition",
    "ds_user_model_preference",
    "ds_model",
    "ds_secret",
    "ds_model_provider",
    "ds_config_entry",
    "ds_audit_log",
  };

  private final JdbcClient jdbc;

  public TestDbHelper(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public void cleanAll() {
    for (String table : TABLES) {
      jdbc.sql("DELETE FROM " + table).update();
    }
  }
}
