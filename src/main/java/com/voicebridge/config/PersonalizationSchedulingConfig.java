package com.voicebridge.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnProperty(
    name = "voicebridge.personalization.scheduling-enabled",
    havingValue = "true",
    matchIfMissing = true)
public class PersonalizationSchedulingConfig {}
