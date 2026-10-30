package com.voicebridge.adapter.in.web;

import com.voicebridge.adapter.in.web.dto.AddPhraseRequest;
import com.voicebridge.adapter.in.web.dto.PhraseResponse;
import com.voicebridge.adapter.in.web.dto.SpeakPhraseResponse;
import com.voicebridge.common.response.ApiResponse;
import com.voicebridge.port.in.AddPhraseUseCase;
import com.voicebridge.port.in.DeletePhraseUseCase;
import com.voicebridge.port.in.ListPhrasesUseCase;
import com.voicebridge.port.in.SpeakPhraseUseCase;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 자주 쓰는 문구 목록·추가·삭제·말하기. API 명세서 10장 참고. */
@RestController
@RequestMapping("/api/v1/phrases")
@RequiredArgsConstructor
public class PhraseController {

  private final ListPhrasesUseCase listPhrasesUseCase;
  private final AddPhraseUseCase addPhraseUseCase;
  private final DeletePhraseUseCase deletePhraseUseCase;
  private final SpeakPhraseUseCase speakPhraseUseCase;

  @GetMapping
  public ApiResponse<List<PhraseResponse>> list(
      @AuthenticationPrincipal UUID userId,
      @RequestParam(name = "category", required = false) String category) {
    return ApiResponse.success(
        listPhrasesUseCase.list(userId, category).stream().map(PhraseResponse::from).toList());
  }

  @PostMapping
  public ResponseEntity<ApiResponse<PhraseResponse>> add(
      @AuthenticationPrincipal UUID userId, @RequestBody AddPhraseRequest request) {
    var result =
        addPhraseUseCase.add(
            new AddPhraseUseCase.AddCommand(userId, request.category(), request.text()));
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(PhraseResponse.from(result)));
  }

  // UUID 모양만 받는다. 그래야 /phrases/suggestions 같은 다른 주소가 이 매핑에 걸려 405가 되지 않는다(추천 기능이 꺼졌을 때 404).
  @DeleteMapping("/{phraseId:[0-9a-fA-F-]{36}}")
  public ResponseEntity<Void> delete(
      @AuthenticationPrincipal UUID userId, @PathVariable("phraseId") UUID phraseId) {
    deletePhraseUseCase.delete(userId, phraseId);
    return ResponseEntity.noContent().build();
  }

  // 누를 때마다 같은 결과를 받지만 처음에는 음성을 만들어 저장하므로 조회(GET)가 아니라 POST다.
  // 응답의 audioUrl은 만료되는 임시 URL이라 캐시하지 않게 한다.
  @PostMapping("/{phraseId}/speak")
  public ResponseEntity<ApiResponse<SpeakPhraseResponse>> speak(
      @AuthenticationPrincipal UUID userId, @PathVariable("phraseId") UUID phraseId) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            ApiResponse.success(
                SpeakPhraseResponse.from(speakPhraseUseCase.speak(userId, phraseId))));
  }
}
