package com.voicebridge.adapter.in.web;

import com.voicebridge.port.in.GetPhraseAudioUseCase;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** local 프로파일 전용 문구 음성 파일 조회. 운영은 S3 임시 URL로 재생하므로 등록하지 않는다. */
@RestController
@Profile("local")
@RequiredArgsConstructor
@RequestMapping("/api/v1/phrases")
public class LocalPhraseAudioController {
  private final GetPhraseAudioUseCase audio;

  @GetMapping("/{phraseId}/audio")
  public ResponseEntity<Resource> getAudio(
      @AuthenticationPrincipal UUID userId, @PathVariable("phraseId") UUID phraseId) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .contentType(MediaType.parseMediaType("audio/mpeg"))
        .body(new ByteArrayResource(audio.getAudio(userId, phraseId)));
  }
}
