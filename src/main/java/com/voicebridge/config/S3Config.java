package com.voicebridge.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
public class S3Config {

  // 크레덴셜은 코드에 두지 않는다. DefaultCredentialsProvider가 환경변수/~/.aws/credentials/IAM 역할
  // 순으로 알아서 찾는다.
  @Bean
  public S3Client s3Client(@Value("${voicebridge.s3.region}") String region) {
    return S3Client.builder()
        .region(Region.of(region))
        .credentialsProvider(DefaultCredentialsProvider.create())
        .build();
  }
}
