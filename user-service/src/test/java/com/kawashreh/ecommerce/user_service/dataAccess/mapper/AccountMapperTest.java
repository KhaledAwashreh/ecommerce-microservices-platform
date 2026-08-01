package com.kawashreh.ecommerce.user_service.dataAccess.mapper;

import com.kawashreh.ecommerce.user_service.dataAccess.entity.AccountEntity;
import com.kawashreh.ecommerce.user_service.domain.model.Account;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// Regression test for GH #32: AccountMapper never mapped the `archived` field, so it
// silently reset to false on every round trip through the mapper.
class AccountMapperTest {

    @Test
    void toEntity_mapsArchivedFlag() {
        Account domain = Account.builder().archived(true).build();

        AccountEntity entity = AccountMapper.toEntity(domain);

        assertThat(entity.isArchived()).isTrue();
    }

    @Test
    void toDomain_mapsArchivedFlag() {
        AccountEntity entity = AccountEntity.builder().archived(true).build();

        Account domain = AccountMapper.toDomain(entity);

        assertThat(domain.isArchived()).isTrue();
    }
}
