package com.siose.ff.logistics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Applies the real Flyway scripts to PostgreSQL 17, the version the cluster runs, and starts the
 * app on the result with {@code ddl-auto: validate}: an entity whose table or column the scripts
 * do not create fails here, not in the cluster. The other tests stay on H2. Skipped where no
 * Docker is reachable (a Windows build without Docker); CI and WSL always run it.
 */
@SpringBootTest
@EnabledIf("dockerAvailable")
class FlywayMigrationTest {

    private static PostgreSQLContainer postgres;

    static boolean dockerAvailable() {
        return DockerClientFactory.instance().isDockerAvailable();
    }

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry registry) {
        // Started here, not in a static block: a skipped class must not need Docker at all.
        postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));
        postgres.start();
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.enabled", () -> "true");
        // The real schema, from the migrations; Hibernate only checks that the entities match it.
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.sql.init.mode", () -> "never");
        // The test config defers database initialization until Hibernate is up, for schema.sql on
        // H2. Flyway counts as database initialization too, so it would run after Hibernate had
        // already validated an empty schema. Here Flyway must come first, as in the cluster.
        registry.add("spring.jpa.defer-datasource-initialization", () -> "false");
    }

    @AfterAll
    static void stop() {
        if (postgres != null) {
            postgres.stop();
        }
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void appliesEveryMigration() {
        List<String> versions = jdbc.queryForList(
                "select version from flyway_schema_history where success order by installed_rank",
                String.class);
        assertThat(versions).containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12");
    }
}
