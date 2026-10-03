package com.voicebridge.adapter.in.web;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.voicebridge.port.in.GetTtsAudioUseCase;
import com.voicebridge.port.in.GetTtsStatusUseCase;
import com.voicebridge.port.in.RequestTtsUseCase;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@org.springframework.test.context.ActiveProfiles("local")
@WebMvcTest({LocalTtsAudioController.class, TtsController.class})
class LocalTtsAudioControllerTest {
  @Autowired MockMvc mvc;
  @MockitoBean GetTtsAudioUseCase audio;
  @MockitoBean GetTtsStatusUseCase status;
  @MockitoBean RequestTtsUseCase request;
  @MockitoBean com.voicebridge.adapter.out.auth.JwtTokenProvider jwt;

  @Test
  void servesAudioAndRangeWithoutCaching() throws Exception {
    UUID user = UUID.randomUUID(), id = UUID.randomUUID();
    when(audio.getAudio(user, id)).thenReturn(new byte[] {1, 2, 3, 4});
    var auth = authentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
    mvc.perform(get("/api/v1/tts/" + id + "/audio").with(auth))
        .andExpect(status().isOk())
        .andExpect(content().contentType("audio/mpeg"))
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(content().bytes(new byte[] {1, 2, 3, 4}));
    mvc.perform(get("/api/v1/tts/" + id + "/audio").header("Range", "bytes=1-2").with(auth))
        .andExpect(status().isPartialContent())
        .andExpect(header().string("Content-Range", "bytes 1-2/4"))
        .andExpect(content().bytes(new byte[] {2, 3}));
  }

  @Test
  void unauthorizedRequestDoesNotReadFile() throws Exception {
    mvc.perform(get("/api/v1/tts/" + UUID.randomUUID() + "/audio"))
        .andExpect(status().is4xxClientError());
    verifyNoInteractions(audio);
  }

  @Test
  void invalidRangeReturns416() throws Exception {
    UUID user = UUID.randomUUID(), id = UUID.randomUUID();
    when(audio.getAudio(user, id)).thenReturn(new byte[] {1, 2, 3});
    mvc.perform(
            get("/api/v1/tts/" + id + "/audio")
                .header("Range", "bytes=10-20")
                .with(
                    authentication(new UsernamePasswordAuthenticationToken(user, null, List.of()))))
        .andExpect(status().isRequestedRangeNotSatisfiable());
  }

  @Test
  void statusResponseIsNotCached() throws Exception {
    UUID user = UUID.randomUUID(), id = UUID.randomUUID();
    when(status.getStatus(user, id))
        .thenReturn(
            new GetTtsStatusUseCase.TtsStatusResult(
                id, "COMPLETED", "https://example.test/audio.mp3?signature=test"));
    mvc.perform(
            get("/api/v1/tts/" + id)
                .with(
                    authentication(new UsernamePasswordAuthenticationToken(user, null, List.of()))))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(
            jsonPath("$.data.audioUrl").value("https://example.test/audio.mp3?signature=test"));
  }
}
