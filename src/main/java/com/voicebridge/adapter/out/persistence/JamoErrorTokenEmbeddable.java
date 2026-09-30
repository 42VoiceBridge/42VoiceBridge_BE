package com.voicebridge.adapter.out.persistence;

import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 스냅샷의 자모 한 줄. 스냅샷 없이 따로 존재할 일이 없어 독립 엔티티가 아니라 스냅샷에 딸린 값으로 저장한다. */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class JamoErrorTokenEmbeddable {

  private String token;
  private String position;
  private int errors;
  private int sampleCount;
  private Double errorRate;
  private String status;
}
