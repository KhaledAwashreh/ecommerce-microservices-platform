package com.kawashreh.ecommerce.user_service.dataAccess.mapper;

import com.kawashreh.ecommerce.user_service.dataAccess.entity.AccountEntity;
import com.kawashreh.ecommerce.user_service.domain.model.Account;

public final class AccountMapper {
    public static AccountEntity toEntity(Account d) {
        if(d == null) return null;

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
                .user(UserMapper.toEntity(d.getUser()))
                .build();
    }

    public static Account toDomain(AccountEntity e) {

        if(e == null) return null;

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
                .user(UserMapper.toDomain(e.getUser()))
                .build();
    }
}
