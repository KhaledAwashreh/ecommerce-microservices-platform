package com.kawashreh.ecommerce.product_service;

import com.kawashreh.ecommerce.product_service.dataAccess.dao.InventoryDeductionRepository;
import com.kawashreh.ecommerce.product_service.dataAccess.entity.InventoryDeductionEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ActiveProfiles("test")
@Transactional
class InventoryDeductionRepositoryTest extends BaseIntegrationTest {

    @Autowired
    private InventoryDeductionRepository inventoryDeductionRepository;

    private UUID orderItemId;
    private UUID productVariationId;

    @BeforeEach
    void setUp() {
        inventoryDeductionRepository.deleteAll();
        orderItemId = UUID.randomUUID();
        productVariationId = UUID.randomUUID();
    }

    @Test
    void save_thenFindByOrderItemId_returnsTheLedgerRow() {
        var entity = InventoryDeductionEntity.builder()
                .orderItemId(orderItemId)
                .productVariationId(productVariationId)
                .deductedQuantity(5)
                .restoredQuantity(0)
                .build();

        inventoryDeductionRepository.save(entity);

        var found = inventoryDeductionRepository.findByOrderItemId(orderItemId);
        assertThat(found).isPresent();
        assertThat(found.get().getDeductedQuantity()).isEqualTo(5);
        assertThat(found.get().getRestoredQuantity()).isEqualTo(0);
    }

    @Test
    void findByOrderItemIdWithLock_returnsEmpty_whenNoRowExists() {
        var found = inventoryDeductionRepository.findByOrderItemIdWithLock(UUID.randomUUID());
        assertThat(found).isEmpty();
    }

    @Test
    void save_rejectsSecondRowForTheSameOrderItemId() {
        inventoryDeductionRepository.save(InventoryDeductionEntity.builder()
                .orderItemId(orderItemId)
                .productVariationId(productVariationId)
                .deductedQuantity(5)
                .restoredQuantity(0)
                .build());

        assertThatThrownBy(() -> {
            inventoryDeductionRepository.saveAndFlush(InventoryDeductionEntity.builder()
                    .orderItemId(orderItemId)
                    .productVariationId(productVariationId)
                    .deductedQuantity(3)
                    .restoredQuantity(0)
                    .build());
        }).isInstanceOf(DataIntegrityViolationException.class);
    }
}
