package com.voicebridge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class VoiceBridgeApplication {

  public static void main(String[] args) {
    SpringApplication.run(VoiceBridgeApplication.class, args);
  }
}
