package com.voicebridge.adapter.in.web;

import com.voicebridge.adapter.in.web.dto.SuggestPhrasesRequest;
import com.voicebridge.adapter.in.web.dto.SuggestPhrasesResponse;
import com.voicebridge.common.response.ApiResponse;
import com.voicebridge.port.in.SuggestPhrasesUseCase;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 문장 확장·문구 추천. API 명세서 10.6절 참고. 후보는 말하지 않는다 — 사용자가 고른 뒤 문구로 저장해야 말할 수 있다. */
@RestController
// 기능 플래그가 꺼져 있으면(기본값) 등록하지 않는다. application.yml의 voicebridge.features 참고.
@ConditionalOnProperty(name = "voicebridge.features.suggestion", havingValue = "true")
@RequestMapping("/api/v1/phrases/suggestions")
@RequiredArgsConstructor
public class PhraseSuggestionController {

  private final SuggestPhrasesUseCase suggestPhrasesUseCase;

  // 호출할 때마다 새로 만들고 호출 횟수를 쓰므로 POST다. 후보는 저장되지 않으니 캐시도 하지 않게 한다.
  @PostMapping
  public ResponseEntity<ApiResponse<SuggestPhrasesResponse>> suggest(
      @AuthenticationPrincipal UUID userId, @RequestBody SuggestPhrasesRequest request) {
    var result =
        suggestPhrasesUseCase.suggest(
            new SuggestPhrasesUseCase.SuggestCommand(userId, request.text(), request.category()));
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(ApiResponse.success(SuggestPhrasesResponse.from(result)));
  }
}
