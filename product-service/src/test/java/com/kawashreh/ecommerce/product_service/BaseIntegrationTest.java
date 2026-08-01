package com.kawashreh.ecommerce.product_service;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

    // `postgres` is a static field declared on this superclass, so it is a single container
    // shared by every subclass in the same JVM fork (e.g. InventoryServiceIntegrationTest and
    // SchemaIntegrationTest both reuse it) - Testcontainers/Ryuk already stops it when the JVM
    // exits. There used to be a manual @AfterAll that called postgres.stop() here; since
    // @AfterAll runs once per test *class*, that stopped the shared container the moment the
    // first integration test class finished, leaving every other integration test class in the
    // same run unable to connect ("Connection refused") - removed.
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("productdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }
}
