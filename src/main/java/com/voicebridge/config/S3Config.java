package com.voicebridge.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
public class S3Config {

  @Bean(destroyMethod = "close")
  @Profile("!local")
  public S3Presigner s3Presigner(@Value("${voicebridge.s3.region}") String region) {
    return S3Presigner.builder()
        .region(Region.of(region))
        .credentialsProvider(DefaultCredentialsProvider.create())
        .build();
  }

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
