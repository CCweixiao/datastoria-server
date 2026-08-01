package io.github.ccweixiao.datastoria.common.config;

import java.net.URI;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Type-safe server-side OAuth endpoints and public client identifiers. */
@Component
@ConfigurationProperties(prefix = "datastoria.oauth")
public class OAuthProperties {

  private final Codex codex = new Codex();
  private final Github github = new Github();

  public Codex getCodex() {
    return codex;
  }

  public Github getGithub() {
    return github;
  }

  public static class Codex {
    private String clientId = "app_EMoamEEZ73f0CkXaXp7hrann";
    private URI tokenUrl = URI.create("https://auth.openai.com/oauth/token");

    public String getClientId() {
      return clientId;
    }

    public void setClientId(String clientId) {
      this.clientId = clientId;
    }

    public URI getTokenUrl() {
      return tokenUrl;
    }

    public void setTokenUrl(URI tokenUrl) {
      this.tokenUrl = tokenUrl;
    }
  }

  public static class Github {
    private String clientId = "";
    private URI deviceCodeUrl = URI.create("https://github.com/login/device/code");
    private URI tokenUrl = URI.create("https://github.com/login/oauth/access_token");

    public String getClientId() {
      return clientId;
    }

    public void setClientId(String clientId) {
      this.clientId = clientId;
    }

    public URI getDeviceCodeUrl() {
      return deviceCodeUrl;
    }

    public void setDeviceCodeUrl(URI deviceCodeUrl) {
      this.deviceCodeUrl = deviceCodeUrl;
    }

    public URI getTokenUrl() {
      return tokenUrl;
    }

    public void setTokenUrl(URI tokenUrl) {
      this.tokenUrl = tokenUrl;
    }
  }
}
