package com.kawashreh.ecommerce.product_service;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

    // GH #61 root cause: `postgres` is a static field, so the *Java object* is shared by
    // every subclass in this JVM fork (SchemaIntegrationTest, InventoryServiceIntegrationTest,
    // InventoryDeductionRepositoryTest, ProductReviewTimestampIntegrationTest all reuse it) -
    // but it used to also be annotated `@Container`, with `@Testcontainers` on this class.
    // That combination only shares a container across *test methods within one class*: per
    // Testcontainers' own JUnit5 extension (TestcontainersExtension#beforeAll/afterAll), the
    // start/stop bookkeeping for a `@Container` field lives in a JUnit5 `ExtensionContext`
    // Store that is scoped *per test class*, not shared across sibling classes. So at the end
    // of every integration test class, the extension called `postgres.stop()` on this shared
    // object (tearing down that Postgres container for real), and at the start of the next
    // class it called `postgres.start()` again - which, since the container was already
    // stopped, created a *brand-new* Postgres container on a new port.
    //
    // That silently orphaned whichever container Spring's test ApplicationContext cache had
    // already wired a DataSource/HikariPool to: Spring only rebuilds a context (re-running
    // `configureProperties` below to pick up the new port) when a test class's merged config
    // differs from what's cached. SchemaIntegrationTest, InventoryServiceIntegrationTest,
    // InventoryDeductionRepositoryTest, and ProductReviewTimestampIntegrationTest all share an
    // *identical* config, so only the first of them to run in a given JVM fork got a context
    // built against the container that was actually alive at that moment; the other three
    // reused the cached context's now-stale DataSource, pointed at a container Testcontainers
    // had already stopped and replaced - hence "HikariPool ... Connection is not available" /
    // "Connection refused" for the rest of the run, and 100% pass rate when any one class runs
    // alone (nothing had torn its container out from under it yet). ProductCacheEvictionIntegrationTest
    // and ProductVariationServiceIntegrationTest were unaffected only because their extra
    // `@TestConfiguration` (an in-memory CacheManager) makes their merged config unique, so
    // they always get a freshly-built context matched to whatever container is live for them.
    //
    // Fix: use Testcontainers' documented "singleton container" pattern instead of
    // `@Container`/`@Testcontainers` - start the container exactly once, in a static
    // initializer, so nothing but JVM exit ever stops it. This is what the shared-static-field
    // design here always intended (see the removed-`@AfterAll` history below); the container
    // annotations were the wrong tool for sharing across classes.
    //
    // There used to be a manual @AfterAll that called postgres.stop() here too; since
    // @AfterAll runs once per test *class*, that stopped the shared container the moment the
    // first integration test class finished, leaving every other integration test class in
    // the same run unable to connect ("Connection refused") - removed. Do not add either a
    // per-class @AfterAll or a `@Container` annotation back onto this field.
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("productdb")
            .withUsername("test")
            .withPassword("test");

    static {
        postgres.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }
}
