package com.voicebridge.adapter.in.web;

import com.voicebridge.adapter.in.web.dto.ConfirmRecognitionRequest;
import com.voicebridge.adapter.in.web.dto.ConfirmationResponse;
import com.voicebridge.adapter.in.web.dto.RecognitionHistoryResponse;
import com.voicebridge.adapter.in.web.dto.RecognitionResponse;
import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import com.voicebridge.common.response.ApiResponse;
import com.voicebridge.port.in.ConfirmRecognitionUseCase;
import com.voicebridge.port.in.GetRecognitionDetailUseCase;
import com.voicebridge.port.in.GetRecognitionHistoryUseCase;
import com.voicebridge.port.in.RecognizeSpeechUseCase;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/recognitions")
@RequiredArgsConstructor
public class RecognitionController {
  // 해당 UseCase를 구현한 객체는 어떤 것이든 받을 수 있도록 다형성 사용하기 위해 사용,
  // 그러면 나중에 세부 구현이 달라져도 의존성을 외부에서 주입 받기 때문에 특정 구현에 의존 없이 사용 가능하기 때문에 위 인터페이스 타입을 사용
  private final RecognizeSpeechUseCase recognizeSpeechUseCase;
  private final GetRecognitionHistoryUseCase getRecognitionHistoryUseCase;
  private final GetRecognitionDetailUseCase getRecognitionDetailUseCase;
  private final ConfirmRecognitionUseCase confirmRecognitionUseCase;

  // Post 요청을 처리하며, HTTP 요청 본문의 Content-Type을 multipart/form-data로 제한한다.
  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<ApiResponse<RecognitionResponse>> recognize(
      // JWT에서 인증된 userId, Http 요청 본문에서 이름이 audioFile인 부분을  MultipartFile로 가져온다.
      @AuthenticationPrincipal UUID userId, @RequestPart("audioFile") MultipartFile audioFile) {
    // audioFile이 비어있는 경우 막는다. 전역핸들러에서 이 예외객체는 400 bad request로 매핑됨
    if (audioFile.isEmpty()) {
      throw new CustomException(ErrorCode.VALIDATION_FAILED, "녹음 파일이 비어 있습니다.");
    }
    // multipart 파일에서 음성 바이트 부분을 꺼내서 바이트 배열 형식의 변수에 대입하며,
    // 서버가 받은 multipart 파일의 내용을 처리하는 과정에서 오류 발생했기 때문에 서버 오류로 처리
    byte[] audioBytes;
    try {
      audioBytes = audioFile.getBytes();
    } catch (IOException e) {
      throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, "녹음 파일을 읽지 못했습니다.");
    }

    // 전달하는 세 값은
    // 1. 처음에 Spring Security에 등록된 UserId
    // 2. multipart 본문에서 읽어온 오디오바이트
    // 3.  multipart 본문에 기재되어 있는 원본 파일 이름 가져오기 이며,
    // result는 계약에서 명시하는 record이며 받은 정보를 기반으로 생성되는 불변 타입이다. 이 타입을 이용해서 API응답을 생성한다.
    var result =
        recognizeSpeechUseCase.recognize(userId, audioBytes, audioFile.getOriginalFilename());
    // OK -> API 명세에 따른 HTTP 200 응답.
    // ApiResponse.success(RecognitionResponse.from(result)) -> 성공 응답 작성하며, result의 값을 토대로 응답 메세지 작성
    return ResponseEntity.status(HttpStatus.OK)
        .body(ApiResponse.success(RecognitionResponse.from(result)));
  }

  @GetMapping
  public ApiResponse<RecognitionHistoryResponse> getHistory(
      @AuthenticationPrincipal UUID userId,
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "20") int size) {
    return ApiResponse.success(
        RecognitionHistoryResponse.from(
            getRecognitionHistoryUseCase.getHistory(userId, page, size)));
  }

  @GetMapping("/{recognitionId}")
  public ApiResponse<RecognitionResponse> getDetail(
      @AuthenticationPrincipal UUID userId, @PathVariable("recognitionId") UUID recognitionId) {
    return ApiResponse.success(
        RecognitionResponse.from(getRecognitionDetailUseCase.getDetail(userId, recognitionId)));
  }

  @PostMapping("/{recognitionId}/confirm")
  public ResponseEntity<ApiResponse<ConfirmationResponse>> confirm(
      @AuthenticationPrincipal UUID userId,
      @PathVariable("recognitionId") UUID recognitionId,
      @Valid @RequestBody ConfirmRecognitionRequest request) {
    var result = confirmRecognitionUseCase.confirm(userId, recognitionId, request.confirmedText());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(ConfirmationResponse.from(result)));
  }
}
