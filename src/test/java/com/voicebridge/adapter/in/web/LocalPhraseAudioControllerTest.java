package com.voicebridge.adapter.in.web;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.voicebridge.adapter.out.auth.JwtTokenProvider;
import com.voicebridge.port.in.GetPhraseAudioUseCase;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("local")
@WebMvcTest(LocalPhraseAudioController.class)
class LocalPhraseAudioControllerTest {
  @Autowired MockMvc mvc;
  @MockitoBean GetPhraseAudioUseCase audio;
  @MockitoBean JwtTokenProvider jwt;
  // JWT 필터가 토큰 주인의 존재를 확인한다(탈퇴 계정 차단). 이 테스트는 인증 객체를 직접 넣으므로 쓰이지 않는다.
  @MockitoBean com.voicebridge.port.out.UserRepositoryPort users;

  @Test
  void 음성을_캐시하지_않고_내려주며_Range도_받는다() throws Exception {
    UUID user = UUID.randomUUID(), id = UUID.randomUUID();
    when(audio.getAudio(user, id)).thenReturn(new byte[] {1, 2, 3, 4});
    var auth = authentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));

    mvc.perform(get("/api/v1/phrases/" + id + "/audio").with(auth))
        .andExpect(status().isOk())
        .andExpect(content().contentType("audio/mpeg"))
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(content().bytes(new byte[] {1, 2, 3, 4}));
    mvc.perform(get("/api/v1/phrases/" + id + "/audio").header("Range", "bytes=1-2").with(auth))
        .andExpect(status().isPartialContent())
        .andExpect(header().string("Content-Range", "bytes 1-2/4"));
  }

  @Test
  void 로그인하지_않으면_파일을_읽지_않는다() throws Exception {
    mvc.perform(get("/api/v1/phrases/" + UUID.randomUUID() + "/audio"))
        .andExpect(status().is4xxClientError());
    verifyNoInteractions(audio);
  }
}
