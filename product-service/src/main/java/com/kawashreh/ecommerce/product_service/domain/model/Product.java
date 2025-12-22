package com.kawashreh.ecommerce.product_service.domain.model;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;


import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
public class Product {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column
    private String description;

    @Column
    @OneToMany
    private List<Category> categories;

    @Column
    @OneToMany
    private List<ProductVariation> variations;

    @CreationTimestamp
    @Column
    private Instant createdAt;

    @CreationTimestamp
    @Column
    private Instant updatedAt;

    @Column
    private String thumbnailUrl;

}
