package com.voicebridge.domain.diagnosis;

import java.util.UUID;

/** 낭독 대상 문장. 진단용/추천용이 공유하는 마스터 데이터. */
public record Sentence(UUID id, String text) {}
