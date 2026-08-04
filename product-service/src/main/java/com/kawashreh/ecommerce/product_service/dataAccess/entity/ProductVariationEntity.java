package com.kawashreh.ecommerce.product_service.dataAccess.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "product_variation")
public class ProductVariationEntity {

    @Id
    @GeneratedValue

    private UUID id;

    @Column(name = "sku", nullable = false, unique = true, length = 50)
    private String sku;

    @Column(name = "stock_quantity", nullable = false)
    private int stockQuantity;

    @Column(name = "name", nullable = false)
    private String name;


    @Column(name = "price", nullable = false)
    private BigDecimal price;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    // This entity imported @CreationTimestamp/@UpdateTimestamp but never actually applied
    // them - it was the only timestamped entity in this module that didn't (ProductEntity,
    // ProductReviewEntity, InventoryEntity and InventoryDeductionEntity all do). The result
    // was that product_variation.created_at/updated_at were never populated at all: both
    // columns were NULL for every row ever inserted, and the create response reported null
    // timestamps. Found live via a smoke test, confirmed straight against the table.
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "thumbnail_url")
    private String thumbnailUrl;

    @ElementCollection
    @Column(name = "attachments")
    private List<UUID> attachments;

    @OneToMany
    @JoinColumn(name = "product_variation_id", nullable = false)
    private List<AttributeEntity> attributes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private ProductEntity product;
}
