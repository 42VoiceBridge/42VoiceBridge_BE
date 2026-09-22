package com.voicebridge.adapter.out.storage;

import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import com.voicebridge.port.out.StoragePort;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

// 로컬 개발 전용. AWS 버킷·크레덴셜 없이도 업로드 흐름을 끝까지 돌려볼 수 있게 한다.
// 반환하는 키 형식은 S3 어댑터와 동일하므로(StorageKeys) Recording.s3Path 의미가 달라지지 않는다.
@Component
@Profile("local")
public class LocalFileStorageAdapter implements StoragePort {

  private final Path baseDir;

  public LocalFileStorageAdapter(
      @Value("${voicebridge.storage.local-dir:./uploads}") String localDir) {
    this.baseDir = Path.of(localDir);
  }

  @Override
  public String upload(byte[] fileBytes, String fileName) {
    String key = StorageKeys.newKey(fileName);
    Path target = baseDir.resolve(key);
    try {
      Files.createDirectories(target.getParent());
      Files.write(target, fileBytes);
    } catch (IOException e) {
      throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, "음성 파일 저장에 실패했습니다.");
    }
    return key;
  }
}
