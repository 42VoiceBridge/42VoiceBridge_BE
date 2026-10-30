package com.voicebridge.adapter.in.web;

import com.voicebridge.adapter.in.web.dto.AddPhraseRequest;
import com.voicebridge.adapter.in.web.dto.AddPhraseResponse;
import com.voicebridge.adapter.in.web.dto.PhraseListResponse;
import com.voicebridge.common.response.ApiResponse;
import com.voicebridge.port.in.AddPhraseUseCase;
import com.voicebridge.port.in.DeletePhraseUseCase;
import com.voicebridge.port.in.GetPhrasesUseCase;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 자주 쓰는 문구 목록·추가·삭제. API 명세서 10장 참고. */
@RestController
@RequestMapping("/api/v1/phrases")
@RequiredArgsConstructor
public class PhraseController {

  private final GetPhrasesUseCase getPhrasesUseCase;
  private final AddPhraseUseCase addPhraseUseCase;
  private final DeletePhraseUseCase deletePhraseUseCase;

  @GetMapping
  public ApiResponse<PhraseListResponse> getPhrases(@AuthenticationPrincipal UUID userId) {
    return ApiResponse.success(PhraseListResponse.from(getPhrasesUseCase.getPhrases(userId)));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<AddPhraseResponse>> add(
      @AuthenticationPrincipal UUID userId, @RequestBody AddPhraseRequest request) {
    var result =
        addPhraseUseCase.add(
            new AddPhraseUseCase.AddCommand(userId, request.category(), request.text()));
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(AddPhraseResponse.from(result)));
  }

  @DeleteMapping("/{phraseId}")
  public ResponseEntity<Void> delete(
      @AuthenticationPrincipal UUID userId, @PathVariable("phraseId") UUID phraseId) {
    deletePhraseUseCase.delete(userId, phraseId);
    return ResponseEntity.noContent().build();
  }
}
