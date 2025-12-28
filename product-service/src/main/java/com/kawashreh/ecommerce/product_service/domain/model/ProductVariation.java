package com.kawashreh.ecommerce.product_service.domain.model;

import com.kawashreh.ecommerce.product_service.infra.models.Attachment;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
public class ProductVariation {

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

    @CreationTimestamp
    @Column
    private Instant updatedAt;

    @Column
    private String thumbnailUrl;

    @ElementCollection
    @CollectionTable(name = "entity_attachments", joinColumns = @JoinColumn(name = "entity_id"))
    @Column(name = "attachment")
    private List<String> attachments;

    @Column
    @OneToMany
    private List<Attribute> categories;

    @JoinColumn(name = "product_id", nullable = false)
    @ManyToOne
    private Product product;
}

