package io.github.ccweixiao.datastoria.boot.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Base64;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MasterKeyFileTest {

  @TempDir Path tempDir;

  @Test
  void generatesKeyFileWithOwnerOnlyPermissionsWhenAbsent() throws IOException {
    Path keyFile = tempDir.resolve("data/master.key");
    String generated = MasterKeyFile.loadOrGenerate(keyFile);

    assertThat(Files.exists(keyFile)).isTrue();
    assertThat(Base64.getDecoder().decode(generated)).hasSize(32);

    // Re-reading the same file must return the identical key, not a fresh one.
    assertThat(MasterKeyFile.loadOrGenerate(keyFile)).isEqualTo(generated);

    Set<PosixFilePermission> perms = Files.getPosixFilePermissions(keyFile);
    assertThat(perms).containsOnly(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
  }

  @Test
  void generatesDistinctKeysAcrossFiles() {
    String a = MasterKeyFile.loadOrGenerate(tempDir.resolve("a.key"));
    String b = MasterKeyFile.loadOrGenerate(tempDir.resolve("b.key"));
    assertThat(a).isNotEqualTo(b);
  }

  @Test
  void readsExistingValidKey() throws IOException {
    String existing = Base64.getEncoder().encodeToString(new byte[32]);
    Path keyFile = tempDir.resolve("master.key");
    Files.writeString(keyFile, existing, StandardCharsets.UTF_8);
    assertThat(MasterKeyFile.loadOrGenerate(keyFile)).isEqualTo(existing);
  }

  @Test
  void malformedKeyFileFailsWithClearError() throws IOException {
    Path keyFile = tempDir.resolve("master.key");
    Files.writeString(keyFile, "not-base64!!!", StandardCharsets.UTF_8);
    assertThatThrownBy(() -> MasterKeyFile.loadOrGenerate(keyFile))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("not valid base64");
  }

  @Test
  void wrongLengthKeyFileFailsWithClearError() throws IOException {
    Path keyFile = tempDir.resolve("master.key");
    Files.writeString(keyFile, Base64.getEncoder().encodeToString(new byte[16]));
    assertThatThrownBy(() -> MasterKeyFile.loadOrGenerate(keyFile))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("exactly 32 bytes");
  }

  @Test
  void emptyKeyFileFailsWithClearError() throws IOException {
    Path keyFile = tempDir.resolve("master.key");
    Files.writeString(keyFile, "   \n", StandardCharsets.UTF_8);
    assertThatThrownBy(() -> MasterKeyFile.loadOrGenerate(keyFile))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("empty");
  }
}
