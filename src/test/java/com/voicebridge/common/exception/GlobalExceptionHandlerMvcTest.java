package com.voicebridge.common.exception;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.voicebridge.domain.personalization.InsufficientRecordingException;
import com.voicebridge.domain.personalization.PersonalizationJob;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** 실제 학습 API 없이 MVC의 예외 선택과 응답 직렬화를 확인하는 테스트 전용 컨트롤러를 사용한다. */
class GlobalExceptionHandlerMvcTest {
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcBuilders.standaloneSetup(new DomainFailureController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void 도메인의_녹음부족_예외는_일반_500_핸들러가_아닌_422_핸들러로_전달된다() throws Exception {
    mvc.perform(get("/test/domain/insufficient"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.data").value(nullValue()))
        .andExpect(jsonPath("$.error.code").value("INSUFFICIENT_RECORDINGS"))
        .andExpect(
            jsonPath("$.error.message").value(new InsufficientRecordingException().getMessage()));
  }

  @ParameterizedTest
  @CsvSource({
    "state,409,INVALID_STATE_TRANSITION",
    "argument,400,VALIDATION_FAILED",
    "unexpected,500,INTERNAL_SERVER_ERROR"
  })
  void 예외_종류별_HTTP_상태와_오류코드를_선택한다(String failure, int httpStatus, String code) throws Exception {
    mvc.perform(get("/test/domain/{failure}", failure))
        .andExpect(status().is(httpStatus))
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.data").value(nullValue()))
        .andExpect(jsonPath("$.error.code").value(code));
  }

  @RestController
  static class DomainFailureController {
    @GetMapping("/test/domain/{failure}")
    public void fail(@PathVariable("failure") String failure) {
      switch (failure) {
        case "insufficient" -> PersonalizationJob.create(UUID.randomUUID(), 4);
        case "argument" -> PersonalizationJob.create(null, 5);
        case "state" -> {
          var job = PersonalizationJob.create(UUID.randomUUID(), 5);
          job.markInProgress();
          job.markInProgress();
        }
        default -> throw new RuntimeException("외부 응답에 노출하지 않을 내부 오류");
      }
    }
  }
}
