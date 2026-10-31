package com.voicebridge.adapter.out.stt;

import static org.assertj.core.api.Assertions.assertThat;

import com.voicebridge.port.out.SpeechToTextPort;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.SimpleBeanDefinitionRegistry;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.type.filter.AssignableTypeFilter;

/**
 * SpeechToTextPort 구현체가 프로파일마다 정확히 하나만 켜지는지 검증한다(JamoStatsPortProfileWiringTest와 같은 방식). 둘 다 켜지면 빈이
 * 중복돼 앱이 뜨지 않고, 둘 다 꺼지면 대화 모드 서비스가 주입받을 구현체가 없다.
 */
class SpeechToTextPortProfileWiringTest {

  private static Set<String> activeBeanSimpleNames(String... activeProfiles) {
    SimpleBeanDefinitionRegistry registry = new SimpleBeanDefinitionRegistry();
    ClassPathBeanDefinitionScanner scanner = new ClassPathBeanDefinitionScanner(registry, false);
    StandardEnvironment environment = new StandardEnvironment();
    environment.setActiveProfiles(activeProfiles);
    scanner.setEnvironment(environment);
    scanner.setIncludeAnnotationConfig(false);
    scanner.addIncludeFilter(new AssignableTypeFilter(SpeechToTextPort.class));
    scanner.scan("com.voicebridge.adapter.out.stt");

    return Arrays.stream(registry.getBeanDefinitionNames())
        .map(
            name -> {
              String className = registry.getBeanDefinition(name).getBeanClassName();
              return className.substring(className.lastIndexOf('.') + 1);
            })
        .collect(Collectors.toSet());
  }

  @Test
  void local_프로파일에서는_Stub_구현체만_활성화된다() {
    assertThat(activeBeanSimpleNames("local")).containsExactly("StubSpeechToTextAdapter");
  }

  @Test
  void 활성_프로파일이_없으면_CLOVA_구현체만_활성화된다() {
    assertThat(activeBeanSimpleNames()).containsExactly("NaverClovaSpeechAdapter");
  }

  @Test
  void prod_프로파일에서는_CLOVA_구현체만_활성화된다() {
    assertThat(activeBeanSimpleNames("prod")).containsExactly("NaverClovaSpeechAdapter");
  }
}
