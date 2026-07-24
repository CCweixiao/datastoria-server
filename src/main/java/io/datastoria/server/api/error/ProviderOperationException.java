package io.datastoria.server.api.error;

public class ProviderOperationException extends RuntimeException {

  private final String code;
  private final int status;

  public ProviderOperationException(String code, int status, String message) {
    super(message);
    this.code = code;
    this.status = status;
  }

  public String code() {
    return code;
  }

  public int status() {
    return status;
  }
}
