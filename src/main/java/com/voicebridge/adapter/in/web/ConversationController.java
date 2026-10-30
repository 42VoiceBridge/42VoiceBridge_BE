package com.voicebridge.adapter.in.web;

import com.voicebridge.adapter.in.web.dto.ConversationTurnResponse;
import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import com.voicebridge.common.response.ApiResponse;
import com.voicebridge.port.in.SpeakConversationCandidateUseCase;
import com.voicebridge.port.in.StartConversationTurnUseCase;
import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 대화 모드. API 명세서 11장 참고. 응답(들은 말, 음성)은 저장하지 않으므로 캐시도 하지 않게 한다. */
@RestController
// 기능 플래그가 꺼져 있으면(기본값) 등록하지 않는다. application.yml의 voicebridge.features 참고.
@ConditionalOnProperty(name = "voicebridge.features.conversation", havingValue = "true")
@RequestMapping("/api/v1/conversation/turns")
@RequiredArgsConstructor
public class ConversationController {

  private static final MediaType AUDIO_MPEG = MediaType.valueOf("audio/mpeg");

  private final StartConversationTurnUseCase startConversationTurnUseCase;
  private final SpeakConversationCandidateUseCase speakConversationCandidateUseCase;

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<ApiResponse<ConversationTurnResponse>> start(
      @AuthenticationPrincipal UUID userId, @RequestPart("audioFile") MultipartFile audioFile) {
    byte[] audioBytes;
    try {
      audioBytes = audioFile.getBytes();
    } catch (IOException e) {
      throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, "녹음 파일을 읽지 못했습니다.");
    }
    var result =
        startConversationTurnUseCase.start(
            new StartConversationTurnUseCase.StartCommand(userId, audioBytes));
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(ApiResponse.success(ConversationTurnResponse.from(result)));
  }

  // 본문 없이 ID만 받는다. 말할 텍스트를 클라이언트가 보낼 방법이 없다.
  @PostMapping("/{turnId}/candidates/{candidateId}/speak")
  public ResponseEntity<byte[]> speak(
      @AuthenticationPrincipal UUID userId,
      @PathVariable UUID turnId,
      @PathVariable UUID candidateId) {
    byte[] audio =
        speakConversationCandidateUseCase.speak(
            new SpeakConversationCandidateUseCase.SpeakCommand(userId, turnId, candidateId));
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .contentType(AUDIO_MPEG)
        .body(audio);
  }
}
