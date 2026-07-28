package io.github.ccweixiao.datastoria.common.error;

public class ResourceInUseException extends RuntimeException {
  public ResourceInUseException(String resourceType, String id) {
    super(resourceType + " " + id + " is still referenced");
  }
}
