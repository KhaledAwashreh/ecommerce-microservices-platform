package com.kawashreh.ecommerce.product_service.dataAccess.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * GH #30: ledger of how much stock was deducted (and how much of that has since been
 * restored) per order item, so restoreStock can enforce a real ceiling instead of an
 * unconditional add. One row per orderItemId, created by deductStock, updated by
 * restoreStock - never re-created.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "inventory_deduction")
public class InventoryDeductionEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "order_item_id", nullable = false, unique = true)
    private UUID orderItemId;

    @Column(name = "product_variation_id", nullable = false)
    private UUID productVariationId;

    @Column(name = "deducted_quantity", nullable = false)
    private int deductedQuantity;

    @Column(name = "restored_quantity", nullable = false)
    private int restoredQuantity;

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
