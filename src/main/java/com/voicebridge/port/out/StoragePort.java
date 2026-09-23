package com.voicebridge.port.out;

public interface StoragePort {

  /** 파일을 저장하고 저장 경로를 반환한다. 반환값은 Recording.s3Path에 그대로 들어간다. */
  String upload(byte[] fileBytes, String fileName);
}
