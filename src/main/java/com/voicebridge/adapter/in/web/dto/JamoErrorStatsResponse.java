package com.voicebridge.adapter.in.web.dto;

import com.voicebridge.port.in.GetJamoErrorStatsUseCase;
import java.util.List;

public record JamoErrorStatsResponse(
    String metricVersion, int minSupport, int sessionsUsed, int pairsUsed, List<TokenDto> tokens) {

  public static JamoErrorStatsResponse from(GetJamoErrorStatsUseCase.StatsResult result) {
    List<TokenDto> tokens =
        result.tokens().stream()
            .map(
                t ->
                    new TokenDto(
                        t.token(),
                        t.position(),
                        t.errors(),
                        t.sampleCount(),
                        t.errorRate(),
                        t.status()))
            .toList();
    return new JamoErrorStatsResponse(
        result.metricVersion(),
        result.minSupport(),
        result.sessionsUsed(),
        result.pairsUsed(),
        tokens);
  }

  public record TokenDto(
      String token,
      String position,
      int errors,
      int sampleCount,
      Double errorRate,
      String status) {}
}
