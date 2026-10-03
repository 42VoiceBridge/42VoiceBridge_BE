package com.voicebridge.application;

import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import com.voicebridge.port.in.TrainPersonalizationModelUseCase;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** The AI v1 train route returns 501 and has no job-status route. No job may be accepted. */
@Service
public class TrainPersonalizationModelService implements TrainPersonalizationModelUseCase {
  @Override
  public TrainResult train(UUID userId) {
    throw new CustomException(ErrorCode.TRAINING_UNAVAILABLE);
  }
}
