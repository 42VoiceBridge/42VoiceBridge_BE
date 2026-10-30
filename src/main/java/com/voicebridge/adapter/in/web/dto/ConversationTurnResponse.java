package com.voicebridge.adapter.in.web.dto;

import com.voicebridge.port.in.StartConversationTurnUseCase.TurnResult;
import java.util.List;
import java.util.UUID;

public record ConversationTurnResponse(
    UUID turnId, String heardText, List<CandidateResponse> candidates) {

  public static ConversationTurnResponse from(TurnResult result) {
    return new ConversationTurnResponse(
        result.turnId(),
        result.heardText(),
        result.candidates().stream().map(c -> new CandidateResponse(c.id(), c.text())).toList());
  }

  public record CandidateResponse(UUID id, String text) {}
}
