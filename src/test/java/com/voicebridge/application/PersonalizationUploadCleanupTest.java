package com.voicebridge.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.voicebridge.domain.personalization.*;
import com.voicebridge.port.out.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PersonalizationUploadCleanupTest {
  private final PersonalizationRecordingRepositoryPort recordings =
      mock(PersonalizationRecordingRepositoryPort.class);
  private final StoragePort storage = mock(StoragePort.class);

  @Test
  void rejectsNonPositiveSettings() {
    assertThatThrownBy(() -> new PersonalizationUploadCleanup(recordings, storage, 0, 300000))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new PersonalizationUploadCleanup(recordings, storage, -1, 300000))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new PersonalizationUploadCleanup(recordings, storage, 30, 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new PersonalizationUploadCleanup(recordings, storage, 30, -1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void expiresAndRetriesFailedStorageDeletion() {
    var recording = mock(PersonalizationRecording.class);
    UUID id = UUID.randomUUID();
    when(recording.id()).thenReturn(id);
    when(recording.storageKey()).thenReturn("personalization/test.wav");
    when(recordings.findExpiredUploaded(any()))
        .thenReturn(List.of(recording))
        .thenReturn(List.of());
    when(recordings.findPendingBefore(any())).thenReturn(List.of(recording));
    when(recordings.claimPendingForCleanup(eq(id), any())).thenReturn(true);
    doThrow(new RuntimeException("unavailable"))
        .doNothing()
        .when(storage)
        .delete("personalization/test.wav");
    var cleanup = new PersonalizationUploadCleanup(recordings, storage, 30, 300000);
    LocalDateTime before = LocalDateTime.now().minusDays(30);
    cleanup.cleanup();
    verify(recordings)
        .findExpiredUploaded(
            argThat(
                cutoff ->
                    !cutoff.isBefore(before)
                        && !cutoff.isAfter(LocalDateTime.now().minusDays(30))));
    verify(recordings).markDeletionPending(id);
    verify(recordings, never()).deletePending(id);
    cleanup.cleanup();
    verify(storage, times(2)).delete("personalization/test.wav");
    verify(recordings).deletePending(id);
  }

  @Test
  void doesNotDeletePendingUploadWithoutCleanupClaim() {
    var recording = mock(PersonalizationRecording.class);
    when(recording.id()).thenReturn(UUID.randomUUID());
    when(recordings.findExpiredUploaded(any())).thenReturn(List.of());
    when(recordings.findPendingBefore(any())).thenReturn(List.of(recording));
    new PersonalizationUploadCleanup(recordings, storage, 30, 300000).cleanup();
    verifyNoInteractions(storage);
    verify(recordings, never()).deletePending(any());
  }
}
