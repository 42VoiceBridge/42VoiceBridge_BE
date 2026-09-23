package com.voicebridge.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

// 이 어노테이션이 없으면 @Async가 아무 효과 없이 동기로 실행된다(에러도 나지 않음).
@Configuration
@EnableAsync
public class AsyncConfig {}
