package com.voicebridge.adapter.in.web;

import com.voicebridge.port.in.GetTtsAudioUseCase;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("local")
@RequiredArgsConstructor
@RequestMapping("/api/v1/tts")
public class LocalTtsAudioController {
  private final GetTtsAudioUseCase audio;

  @GetMapping("/{ttsId}/audio")
  public ResponseEntity<Resource> getAudio(
      @AuthenticationPrincipal UUID userId, @PathVariable("ttsId") UUID ttsId) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .contentType(MediaType.parseMediaType("audio/mpeg"))
        .body(new ByteArrayResource(audio.getAudio(userId, ttsId)));
  }
}
