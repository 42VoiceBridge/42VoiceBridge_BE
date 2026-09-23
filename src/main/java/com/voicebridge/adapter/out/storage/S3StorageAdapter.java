package com.voicebridge.adapter.out.storage;

import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import com.voicebridge.port.out.StoragePort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

// 로컬에서는 실제 AWS 버킷·크레덴셜이 필요해 개발이 막히므로 LocalFileStorageAdapter로 대체한다.
// TODO: 팀에 S3 버킷/크레덴셜이 준비되면 로컬에서도 실물로 검증할지 논의할 것.
@Component
@Profile("!local")
public class S3StorageAdapter implements StoragePort {

  private final S3Client s3Client;
  private final String bucket;

  public S3StorageAdapter(S3Client s3Client, @Value("${voicebridge.s3.bucket}") String bucket) {
    this.s3Client = s3Client;
    this.bucket = bucket;
  }

  @Override
  public String upload(byte[] fileBytes, String fileName) {
    String key = StorageKeys.newKey(fileName);
    try {
      s3Client.putObject(
          PutObjectRequest.builder().bucket(bucket).key(key).build(),
          RequestBody.fromBytes(fileBytes));
    } catch (SdkException e) {
      throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, "음성 파일 저장에 실패했습니다.");
    }
    return key;
  }
}
