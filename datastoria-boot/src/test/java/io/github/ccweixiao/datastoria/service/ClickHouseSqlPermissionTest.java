package io.github.ccweixiao.datastoria.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import io.github.ccweixiao.datastoria.common.crypto.EnvelopeEncryptionService;
import io.github.ccweixiao.datastoria.common.domain.ClickHouseConnection;
import io.github.ccweixiao.datastoria.common.identity.Identity;
import io.github.ccweixiao.datastoria.dao.repository.ClickHouseConnectionRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@ExtendWith(MockitoExtension.class)
class ClickHouseSqlPermissionTest {

  @Mock private ClickHouseConnectionRepository repository;
  @Mock private ClickHouseRemoteClient remoteClient;
  @Mock private EnvelopeEncryptionService crypto;
  private ClickHouseConnectionService service;

  @BeforeEach
  void setUp() {
    service =
        new ClickHouseConnectionService(repository, crypto, remoteClient, Schedulers.immediate());
  }

  /**
   * Enabled connection with no password cipher so decryptPassword short-circuits (no crypto call).
   */
  private ClickHouseConnection connection() {
    return new ClickHouseConnection(
        "c1",
        "default",
        "owner",
        "test",
        "http://ch:8123",
        "chuser",
        "mycluster",
        null,
        null,
        null,
        null,
        true,
        0,
        Instant.now(),
        Instant.now(),
        null);
  }

  private Identity admin() {
    return new Identity("default", "admin-user", Set.of("ROLE_ADMIN", "ROLE_USER"));
  }

  private Identity regularUser() {
    return new Identity("default", "plain-user", Set.of("ROLE_USER"));
  }

  @Test
  void regularUserDdlIsRejectedBeforeReachingClickHouse() {
    when(repository.findById("c1", "default", "plain-user")).thenReturn(Optional.of(connection()));

    Mono<?> result =
        service.queryStream("c1", "DROP TABLE sensitive", Map.of(), null, null, regularUser());

    assertThatThrownBy(result::block).isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(remoteClient);
  }

  @Test
  void adminDdlReachesClickHouseUnchanged() {
    when(repository.findById("c1", "default", "admin-user")).thenReturn(Optional.of(connection()));
    ClickHouseRemoteClient.RemoteQueryResponse stub =
        new ClickHouseRemoteClient.RemoteQueryResponse(
            HttpStatus.OK, new HttpHeaders(), Flux.<DataBuffer>empty());
    when(remoteClient.executeStream(any(), anyString(), anyString(), any()))
        .thenReturn(Mono.just(stub));

    service.queryStream("c1", "DROP TABLE sensitive", Map.of(), null, null, admin()).block();

    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    verify(remoteClient).executeStream(any(), anyString(), sql.capture(), any());
    assertThat(sql.getValue()).contains("DROP TABLE sensitive");
  }

  @Test
  void regularUserSelectReachesClickHouse() {
    when(repository.findById("c1", "default", "plain-user")).thenReturn(Optional.of(connection()));
    ClickHouseRemoteClient.RemoteQueryResponse stub =
        new ClickHouseRemoteClient.RemoteQueryResponse(
            HttpStatus.OK, new HttpHeaders(), Flux.<DataBuffer>empty());
    when(remoteClient.executeStream(any(), anyString(), anyString(), any()))
        .thenReturn(Mono.just(stub));

    service.queryStream("c1", "SELECT 1", Map.of(), null, null, regularUser()).block();

    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    verify(remoteClient).executeStream(any(), anyString(), sql.capture(), any());
    assertThat(sql.getValue()).startsWith("SELECT 1");
  }
}
