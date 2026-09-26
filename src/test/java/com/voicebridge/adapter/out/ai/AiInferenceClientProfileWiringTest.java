package com.voicebridge.adapter.out.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.voicebridge.port.out.AiInferenceClient;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.SimpleBeanDefinitionRegistry;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.type.filter.AssignableTypeFilter;

/**
 * AiInferenceClient 구현체들의 @Profile 조건이 프로파일별로 정확히 하나만 활성화되는지 검증한다. 실제 JPyRustBridge 네이티브 초기화 없이,
 * Spring의 ClassPathBeanDefinitionScanner로 프로파일 조건 평가만 실행한다(빈 인스턴스화는 하지 않음) — jpyrust-experiment
 * 프로파일에서 HttpAiInferenceClient의 !local 조건도 함께 참이 되어 AiInferenceClient 빈이 중복 등록되는 회귀를 방지한다.
 */
class AiInferenceClientProfileWiringTest {

  private static Set<String> activeBeanSimpleNames(String... activeProfiles) {
    SimpleBeanDefinitionRegistry registry = new SimpleBeanDefinitionRegistry();
    ClassPathBeanDefinitionScanner scanner = new ClassPathBeanDefinitionScanner(registry, false);
    StandardEnvironment environment = new StandardEnvironment();
    environment.setActiveProfiles(activeProfiles);
    scanner.setEnvironment(environment);
    scanner.setIncludeAnnotationConfig(false);
    scanner.addIncludeFilter(new AssignableTypeFilter(AiInferenceClient.class));
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
  void jpyrust_experiment_프로파일에서는_JPyRust_구현체만_활성화된다() {
    Set<String> active = activeBeanSimpleNames("jpyrust-experiment");

    assertThat(active).containsExactly("JPyRustAiInferenceClient");
  }

  @Test
  void local_프로파일에서는_Stub_구현체만_활성화된다() {
    Set<String> active = activeBeanSimpleNames("local");

    assertThat(active).containsExactly("StubAiInferenceClient");
  }

  @Test
  void 활성_프로파일이_없으면_Http_구현체만_활성화된다() {
    Set<String> active = activeBeanSimpleNames();

    assertThat(active).containsExactly("HttpAiInferenceClient");
  }
}
