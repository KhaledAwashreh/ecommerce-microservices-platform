package com.kawashreh.ecommerce.product_service.dataAccess.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
public class ProductEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column
    private String description;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinColumn
    private List<CategoryEntity> categories;

    @CreationTimestamp
    @Column
    private Instant createdAt;

    @UpdateTimestamp
    @Column
    private Instant updatedAt;

    @Column
    private String thumbnailUrl;

}
