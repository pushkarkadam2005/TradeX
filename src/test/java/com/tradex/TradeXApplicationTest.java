package com.tradex;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class TradeXApplicationTest {

    @Autowired
    private Environment environment;

    @Test
    @DisplayName("A. Application Context — Spring context starts successfully")
    void contextLoads() {
        assertThat(environment).isNotNull();
    }

    @Test
    @DisplayName("D. Configuration — Test profile loads successfully")
    void testProfileLoadsSuccessfully() {
        assertThat(environment.getActiveProfiles()).contains("test");
        assertThat(environment.getProperty("spring.application.name")).isEqualTo("tradex");
    }

    @Test
    @DisplayName("E. Flyway — Migration directory 'db/migration' exists on classpath")
    void flywayMigrationLocationExistsOnClasspath() {
        URL migrationUrl = getClass().getClassLoader().getResource("db/migration");
        assertThat(migrationUrl).isNotNull();
    }
}
