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
public class ProductVariationEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String sku;

    @Column(nullable = false)
    private int stockQuantity;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(nullable = false)
    private Boolean isActive;

    @CreationTimestamp
    @Column
    private Instant createdAt;

    @UpdateTimestamp
    @Column
    private Instant updatedAt;

    @Column
    private String thumbnailUrl;

    @ElementCollection
    @CollectionTable(name = "entity_attachments", joinColumns = @JoinColumn(name = "entity_id"))
    @Column(name = "attachment")
    private List<String> attachments;

    @OneToMany
    @JoinColumn(name = "category_id", nullable = false)
    private List<AttributeEntity> attributes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private ProductEntity product;
}
