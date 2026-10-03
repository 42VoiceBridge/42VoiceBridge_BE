package com.voicebridge.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;

/**
 * 운영과 같은 순서(빈 MySQL → Flyway 마이그레이션 → Hibernate validate)로 앱이 뜨는지 확인한다. 엔티티를 바꾸고 마이그레이션 파일을 빠뜨리면
 * validate가 실패해 이 테스트가 깨진다.
 *
 * <p>MySqlContainerTest의 공용 컨테이너는 다른 테스트가 ddl-auto로 테이블을 만들어 비어 있지 않으므로, 빈 DB를 쓰는 전용 컨테이너를 띄운다.
 */
@SpringBootTest
class FlywayMigrationTest {

  private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0");

  static {
    MYSQL.start();
  }

  // @DynamicPropertySource는 application.yml보다 우선한다. 테스트 설정(H2, create-drop, Flyway 끔)이 이 값을 덮지 못한다.
  @DynamicPropertySource
  static void productionLikeSchema(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
    registry.add("spring.datasource.username", MYSQL::getUsername);
    registry.add("spring.datasource.password", MYSQL::getPassword);
    registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
    registry.add("spring.flyway.enabled", () -> "true");
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
  }

  @Autowired Environment environment;
  @Autowired JdbcTemplate jdbc;

  @Test
  void 빈_MySQL에_마이그레이션을_적용하면_엔티티_검사를_통과해_앱이_뜬다() {
    // 설정이 실제로 적용됐는지 먼저 본다. H2나 create-drop으로 떴다면 이 테스트는 아무것도 증명하지 못한다.
    assertThat(environment.getProperty("spring.datasource.url")).startsWith("jdbc:mysql:");
    assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");

    List<String> applied =
        jdbc.queryForList(
            "SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank",
            String.class);
    assertThat(applied).startsWith("1");

    Integer tables =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.TABLES"
                + " WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME <> 'flyway_schema_history'",
            Integer.class);
    assertThat(tables).isEqualTo(14);
  }

  @Test
  void 한글은_utf8mb4로_저장된다() {
    Integer notUtf8mb4 =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.TABLES"
                + " WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME <> 'flyway_schema_history'"
                + " AND TABLE_COLLATION NOT LIKE 'utf8mb4%'",
            Integer.class);
    assertThat(notUtf8mb4).isZero();
  }
}
