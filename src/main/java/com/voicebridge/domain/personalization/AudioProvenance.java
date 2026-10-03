package com.voicebridge.domain.personalization;

/** 정규화 결과 중 학습 입력 재현과 감사에 필요한 출처 정보. */
public record AudioProvenance(
    String sourceFormat,
    String sourceCodec,
    int sourceSampleRate,
    int sourceChannels,
    int sampleCount,
    String sourceSha256,
    String wavSha256,
    String normalizationVersion) {}
