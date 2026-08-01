package com.kawashreh.ecommerce.product_service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression coverage for the product-service schema issues GH #27, #38, #39. Runs against
 * a real Postgres Testcontainer with {@code ddl-auto=create-drop} (see
 * {@link BaseIntegrationTest}), so the schema asserted on here is exactly what Hibernate
 * generates from the current entity mappings - the same mechanism dev/docker relies on
 * (`ddl-auto: update`).
 */
@ActiveProfiles("test")
class SchemaIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private List<String> columnsOf(String tableName) {
        return jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE lower(table_name) = lower(?)",
                String.class, tableName);
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE lower(table_name) = lower(?)",
                Integer.class, tableName);
        return count != null && count > 0;
    }

    /**
     * GH #27: ProductVariationEntity.attributes was mapped with
     * {@code @JoinColumn(name = "category_id")} - a copy-paste from the unrelated
     * product-to-category mapping. The FK column on the {@code attribute} table that backs
     * a variation's attributes must not be named after a category.
     */
    @Test
    void attributeTable_hasVariationForeignKey_notCategoryId() {
        List<String> columns = columnsOf("attribute");

        assertThat(columns)
                .as("attribute table columns")
                .doesNotContain("category_id");
        assertThat(columns)
                .as("attribute table must have a FK column back to product_variation")
                .contains("product_variation_id");
    }

    /**
     * GH #38: {@code infra.models.Attachment} was an orphan {@code @Entity} referenced by no
     * repository/service/mapper, yet still created an {@code attachment} table under
     * {@code ddl-auto=update}. The fix direction was wire-it-up or delete-it; since nothing
     * in the codebase referenced it, it was deleted - so no {@code attachment} table should
     * be generated at all.
     */
    @Test
    void noOrphanAttachmentTable_isGenerated() {
        assertThat(tableExists("attachment")).isFalse();
    }

    /**
     * GH #39: {@code CategoryEntity} mapped to a table named {@code categry} (typo), while
     * the unrelated product-to-category join table was named {@code category} - so the
     * table actually called {@code category} was not the category table. Renamed to
     * {@code categories} and {@code product_categories} per the issue's fix direction.
     */
    @Test
    void categoryTables_areNamedCorrectly() {
        assertThat(tableExists("categry")).as("old typo'd table name must be gone").isFalse();
        assertThat(tableExists("categories")).as("category entity table").isTrue();

        assertThat(tableExists("product_categories")).as("product/category join table").isTrue();
        // The old join table was confusingly named "category" (same word as the category
        // entity table's typo'd-but-close name). It must no longer be the join table's name.
        List<String> joinColumns = columnsOf("product_categories");
        assertThat(joinColumns).contains("product_id", "category_id");
    }
}
