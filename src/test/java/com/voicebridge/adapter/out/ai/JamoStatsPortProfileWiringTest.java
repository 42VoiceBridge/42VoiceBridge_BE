package com.voicebridge.adapter.out.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.voicebridge.port.out.JamoStatsPort;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.SimpleBeanDefinitionRegistry;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.type.filter.AssignableTypeFilter;

/**
 * JamoStatsPort 구현체가 프로파일마다 정확히 하나만 켜지는지 검증한다(AiInferenceClientProfileWiringTest와 같은 방식). 인식 어댑터와
 * 달리 jpyrust-experiment에서도 HTTP 구현체가 켜져야 한다 — 자모 통계에는 JPyRust 대체 구현이 없어서, 꺼지면 그 프로파일에서 앱이 뜨지 않는다.
 */
class JamoStatsPortProfileWiringTest {

  private static Set<String> activeBeanSimpleNames(String... activeProfiles) {
    SimpleBeanDefinitionRegistry registry = new SimpleBeanDefinitionRegistry();
    ClassPathBeanDefinitionScanner scanner = new ClassPathBeanDefinitionScanner(registry, false);
    StandardEnvironment environment = new StandardEnvironment();
    environment.setActiveProfiles(activeProfiles);
    scanner.setEnvironment(environment);
    scanner.setIncludeAnnotationConfig(false);
    scanner.addIncludeFilter(new AssignableTypeFilter(JamoStatsPort.class));
    scanner.scan("com.voicebridge.adapter.out.ai");

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
    assertThat(activeBeanSimpleNames("local")).containsExactly("StubJamoStatsClient");
  }

  @Test
  void 활성_프로파일이_없으면_Http_구현체만_활성화된다() {
    assertThat(activeBeanSimpleNames()).containsExactly("HttpJamoStatsClient");
  }

  @Test
  void jpyrust_experiment_프로파일에서도_Http_구현체가_활성화된다() {
    assertThat(activeBeanSimpleNames("jpyrust-experiment")).containsExactly("HttpJamoStatsClient");
  }
}
