package io.github.ccweixiao.datastoria.boot.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads or first-generates the base64 master key file used when no explicit key is configured.
 * Generated files are created owner-read-write (0600 where POSIX permissions apply).
 */
final class MasterKeyFile {

  private static final Logger log = LoggerFactory.getLogger(MasterKeyFile.class);
  private static final int KEY_LENGTH_BYTES = 32;

  private MasterKeyFile() {}

  /** Returns the base64 key from {@code path}, generating a fresh key there when absent. */
  static String loadOrGenerate(Path path) {
    if (Files.exists(path)) {
      return read(path);
    }
    String generated = generate();
    write(path, generated);
    log.warn(
        "No datastoria.master-key configured; generated a new master key at {}. "
            + "Back this file up: losing it makes stored credentials unrecoverable.",
        path.toAbsolutePath());
    return generated;
  }

  private static String read(Path path) {
    String content;
    try {
      content = Files.readString(path, StandardCharsets.UTF_8).trim();
    } catch (IOException e) {
      throw new IllegalStateException("Failed to read master key file " + path, e);
    }
    if (content.isEmpty()) {
      throw new IllegalStateException("Master key file " + path + " is empty");
    }
    validate(path, content);
    return content;
  }

  private static String generate() {
    byte[] keyBytes = new byte[KEY_LENGTH_BYTES];
    new SecureRandom().nextBytes(keyBytes);
    return Base64.getEncoder().encodeToString(keyBytes);
  }

  private static void write(Path path, String base64Key) {
    validate(path, base64Key);
    try {
      if (path.getParent() != null) {
        Files.createDirectories(path.getParent());
      }
      Files.createFile(path, permissions());
      Files.writeString(path, base64Key, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to write master key file " + path, e);
    }
  }

  private static void validate(Path path, String base64Key) {
    byte[] decoded;
    try {
      decoded = Base64.getDecoder().decode(base64Key);
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException("Master key file " + path + " is not valid base64", e);
    }
    if (decoded.length != KEY_LENGTH_BYTES) {
      throw new IllegalStateException(
          "Master key file "
              + path
              + " must decode to exactly "
              + KEY_LENGTH_BYTES
              + " bytes for AES-256, got "
              + decoded.length);
    }
  }

  private static FileAttribute<?>[] permissions() {
    try {
      Set<PosixFilePermission> ownerOnly = PosixFilePermissions.fromString("rw-------"); // 0600
      return new FileAttribute<?>[] {PosixFilePermissions.asFileAttribute(ownerOnly)};
    } catch (UnsupportedOperationException e) {
      // Filesystems without POSIX permissions (e.g. some Windows volumes): default attributes.
      return new FileAttribute<?>[0];
    }
  }
}
