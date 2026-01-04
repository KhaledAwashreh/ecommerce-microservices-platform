package com.kawashreh.ecommerce.user_service.dataAccess.Mapper;

import com.kawashreh.ecommerce.user_service.dataAccess.entity.AccountEntity;
import com.kawashreh.ecommerce.user_service.domain.model.Account;
import org.springframework.stereotype.Component;

public final class AccountMapper {
    public static AccountEntity toEntity(Account d) {
        return AccountEntity.builder()
                .id(d.getId())
                .accountStatus(d.getAccountStatus())
                .accountType(d.getAccountType())
                .activated(d.isActivated())
                .phoneVerified(d.isPhoneVerified())
                .emailVerified(d.isEmailVerified())
                .locale(d.getLocale())
                .timeZone(d.getTimeZone())
                .createdAt(d.getCreatedAt())
                .updatedAt(d.getUpdatedAt())
                .build();
    }

    public static Account toDomain(AccountEntity e) {

        return Account.builder()
                .id(e.getId())
                .accountStatus(e.getAccountStatus())
                .accountType(e.getAccountType())
                .activated(e.isActivated())
                .phoneVerified(e.isPhoneVerified())
                .emailVerified(e.isEmailVerified())
                .locale(e.getLocale())
                .timeZone(e.getTimeZone())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }
}
