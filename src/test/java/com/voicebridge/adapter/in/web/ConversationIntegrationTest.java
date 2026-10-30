package com.voicebridge.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.voicebridge.adapter.out.persistence.PhraseJpaRepository;
import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import com.voicebridge.domain.conversation.ConversationTurn;
import com.voicebridge.port.out.AudioNormalizationPort;
import com.voicebridge.port.out.AudioNormalizationPort.Metadata;
import com.voicebridge.port.out.AudioNormalizationPort.NormalizedAudio;
import com.voicebridge.port.out.CallRateLimitPort;
import com.voicebridge.port.out.ConversationTurnStorePort;
import com.voicebridge.port.out.InvalidAudioException;
import com.voicebridge.port.out.LlmSuggestionPort;
import com.voicebridge.port.out.SpeechToTextPort;
import com.voicebridge.port.out.StoragePort;
import com.voicebridge.port.out.TtsEnginePort;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// 서비스는 실제 빈, 외부 연동(변환기·STT·LLM·TTS·호출 상한·턴 저장소)만 가짜로 둔다. 턴 저장소는 메모리 맵으로 흉내 낸다.
// Redis 저장·만료는 RedisConversationTurnStoreAdapterTest, CSR·Claude 형식은 각 어댑터 테스트가, 실제 키 왕복은 수동 E2E가
// 맡는다.
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "voicebridge.features.conversation=true")
class ConversationIntegrationTest {

  private static final String URL = "/api/v1/conversation/turns";
  private static final byte[] UPLOAD = {1, 2, 3};
  private static final byte[] WAV = {82, 73, 70, 70};
  private static final byte[] MP3 = {73, 68, 51, 4};

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper objectMapper;
  @Autowired PhraseJpaRepository phraseJpaRepository;
  @MockitoBean AudioNormalizationPort normalizer;
  @MockitoBean SpeechToTextPort stt;
  @MockitoBean LlmSuggestionPort llm;
  @MockitoBean TtsEnginePort tts;
  @MockitoBean CallRateLimitPort rateLimit;
  @MockitoBean ConversationTurnStorePort turnStore;
  @MockitoBean StoragePort storage;

  private final Map<UUID, ConversationTurn> turns = new ConcurrentHashMap<>();
  private final UUID me = UUID.randomUUID();
  private final UUID other = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    phraseJpaRepository.deleteAllInBatch();
    turns.clear();
    doAnswer(
            invocation -> {
              ConversationTurn turn = invocation.getArgument(0);
              turns.put(turn.getId(), turn);
              return null;
            })
        .when(turnStore)
        .save(any(), any());
    doAnswer(invocation -> Optional.ofNullable(turns.get((UUID) invocation.getArgument(0))))
        .when(turnStore)
        .findById(any());
    doReturn(new NormalizedAudio(WAV, metadata())).when(normalizer).normalize(any());
    doReturn(true).when(rateLimit).tryAcquire(any(), any(), anyInt(), any());
    doReturn("식사하셨어요?").when(stt).transcribe(any());
    doReturn(List.of("네, 먹었어요.", "아니요, 아직이요.", "", "줄\n바꿈")).when(llm).replyTo("식사하셨어요?");
    doReturn(MP3).when(tts).synthesize(any());
  }

  private static Metadata metadata() {
    return new Metadata("webm", "opus", 48000, 1, 16000, "src", "wav", "v1");
  }

  private MockHttpServletRequestBuilder as(UUID userId, MockHttpServletRequestBuilder request) {
    return request.with(
        authentication(new UsernamePasswordAuthenticationToken(userId, null, List.of())));
  }

  private MockHttpServletRequestBuilder startRequest(UUID userId, byte[] audio) {
    return as(
        userId,
        multipart(URL).file(new MockMultipartFile("audioFile", "a.webm", "audio/webm", audio)));
  }

  private JsonNode startTurn() throws Exception {
    String body =
        mvc.perform(startRequest(me, UPLOAD))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(body).path("data");
  }

  private String speakUrl(Object turnId, Object candidateId) {
    return URL + "/" + turnId + "/candidates/" + candidateId + "/speak";
  }

  @Test
  void 턴을_만들면_들은_말과_걸러진_후보를_돌려주고_저장소에_남기지_않는다() throws Exception {
    mvc.perform(startRequest(me, UPLOAD))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.data.turnId").isNotEmpty())
        .andExpect(jsonPath("$.data.heardText").value("식사하셨어요?"))
        .andExpect(jsonPath("$.data.candidates.length()").value(2))
        .andExpect(jsonPath("$.data.candidates[0].id").isNotEmpty())
        .andExpect(jsonPath("$.data.candidates[0].text").value("네, 먹었어요."))
        .andExpect(jsonPath("$.data.candidates[1].text").value("아니요, 아직이요."));

    verify(stt).transcribe(WAV);
    verify(rateLimit).tryAcquire(eq(me), eq("conversation"), eq(10), any());
    // 자동으로 말하지 않는다
    verifyNoInteractions(tts, storage);
    assertThat(phraseJpaRepository.count()).isZero();
    assertThat(turns).hasSize(1);
  }

  @Test
  void 답변_후보를_못_만들어도_들은_말은_200으로_돌려준다() throws Exception {
    doThrow(new CustomException(ErrorCode.SUGGESTION_UNAVAILABLE)).when(llm).replyTo(any());

    mvc.perform(startRequest(me, UPLOAD))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.heardText").value("식사하셨어요?"))
        .andExpect(jsonPath("$.data.candidates.length()").value(0));
  }

  @Test
  void 빈_녹음은_400이고_외부를_부르지_않는다() throws Exception {
    mvc.perform(startRequest(me, new byte[0]))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    verifyNoInteractions(normalizer, rateLimit, stt, llm);
  }

  @Test
  void 잘못된_녹음은_400이고_호출_횟수를_쓰지_않는다() throws Exception {
    doThrow(new InvalidAudioException(InvalidAudioException.Reason.TOO_SHORT, "짧음"))
        .when(normalizer)
        .normalize(any());

    mvc.perform(startRequest(me, UPLOAD))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("AUDIO_TOO_SHORT"));
    verifyNoInteractions(rateLimit, stt, llm);
  }

  @Test
  void 턴_호출_상한을_넘으면_429이고_STT를_부르지_않는다() throws Exception {
    doReturn(false).when(rateLimit).tryAcquire(any(), eq("conversation"), anyInt(), any());

    mvc.perform(startRequest(me, UPLOAD))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.error.code").value("TOO_MANY_REQUESTS"));
    verifyNoInteractions(stt, llm);
  }

  @Test
  void 받아쓰기가_실패하면_503이고_턴을_만들지_않는다() throws Exception {
    doThrow(new CustomException(ErrorCode.STT_UNAVAILABLE)).when(stt).transcribe(any());

    mvc.perform(startRequest(me, UPLOAD))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.error.code").value("STT_UNAVAILABLE"));
    verifyNoInteractions(llm);
    assertThat(turns).isEmpty();
  }

  @Test
  void 발급한_후보를_말하면_mp3를_바로_돌려주고_저장하지_않는다() throws Exception {
    JsonNode turn = startTurn();
    String turnId = turn.path("turnId").asText();
    String candidateId = turn.path("candidates").get(1).path("id").asText();

    mvc.perform(as(me, post(speakUrl(turnId, candidateId))))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", "audio/mpeg"))
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(content().bytes(MP3));

    verify(tts).synthesize("아니요, 아직이요.");
    verify(rateLimit).tryAcquire(eq(me), eq("conversation-speak"), eq(20), any());
    verifyNoInteractions(storage);
  }

  @Test
  void 다른_사용자의_턴은_말할_수_없다() throws Exception {
    JsonNode turn = startTurn();
    String candidateId = turn.path("candidates").get(0).path("id").asText();

    mvc.perform(as(other, post(speakUrl(turn.path("turnId").asText(), candidateId))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("FORBIDDEN_ACCESS"));
    verifyNoInteractions(tts);
  }

  @Test
  void 없거나_만료된_턴은_404다() throws Exception {
    mvc.perform(as(me, post(speakUrl(UUID.randomUUID(), UUID.randomUUID()))))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    verifyNoInteractions(tts);
  }

  @Test
  void 위조한_후보_ID는_404다() throws Exception {
    JsonNode first = startTurn();
    JsonNode second = startTurn();
    // 같은 사용자의 다른 턴에서 발급된 후보도 이 턴에서는 쓸 수 없다
    String otherTurnsCandidate = second.path("candidates").get(0).path("id").asText();

    for (String candidateId : List.of(UUID.randomUUID().toString(), otherTurnsCandidate)) {
      mvc.perform(as(me, post(speakUrl(first.path("turnId").asText(), candidateId))))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }
    verifyNoInteractions(tts);
  }

  @Test
  void 임의의_텍스트는_말할_수_없다() throws Exception {
    JsonNode turn = startTurn();
    String turnId = turn.path("turnId").asText();

    // 텍스트를 경로에 넣어도 UUID가 아니라 거절된다
    mvc.perform(as(me, post(speakUrl(turnId, "아무 말이나")))).andExpect(status().isBadRequest());
    // 본문에 텍스트를 실어도 무시되고 발급된 후보 텍스트만 합성한다
    String candidateId = turn.path("candidates").get(0).path("id").asText();
    mvc.perform(
            as(me, post(speakUrl(turnId, candidateId)))
                .contentType("application/json")
                .content("{\"text\": \"아무 말이나\"}"))
        .andExpect(status().isOk());

    verify(tts).synthesize("네, 먹었어요.");
    verify(tts, never()).synthesize("아무 말이나");
  }

  @Test
  void 말하기_호출_상한을_넘으면_429이고_합성하지_않는다() throws Exception {
    JsonNode turn = startTurn();
    doReturn(false).when(rateLimit).tryAcquire(any(), eq("conversation-speak"), anyInt(), any());

    mvc.perform(
            as(
                me,
                post(
                    speakUrl(
                        turn.path("turnId").asText(),
                        turn.path("candidates").get(0).path("id").asText()))))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.error.code").value("TOO_MANY_REQUESTS"));
    verifyNoInteractions(tts);
  }

  @Test
  void 합성이_실패하면_503이다() throws Exception {
    JsonNode turn = startTurn();
    doThrow(new IllegalStateException("clova down")).when(tts).synthesize(any());

    mvc.perform(
            as(
                me,
                post(
                    speakUrl(
                        turn.path("turnId").asText(),
                        turn.path("candidates").get(0).path("id").asText()))))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.error.code").value("TTS_UNAVAILABLE"));
  }

  @Test
  void 로그인하지_않으면_401이고_외부를_부르지_않는다() throws Exception {
    mvc.perform(
            multipart(URL).file(new MockMultipartFile("audioFile", "a.webm", "audio/webm", UPLOAD)))
        .andExpect(status().isUnauthorized());
    mvc.perform(post(speakUrl(UUID.randomUUID(), UUID.randomUUID())))
        .andExpect(status().isUnauthorized());
    verifyNoInteractions(normalizer, stt, llm, tts);
  }
}
