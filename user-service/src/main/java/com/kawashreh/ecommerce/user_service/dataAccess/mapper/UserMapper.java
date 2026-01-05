package com.kawashreh.ecommerce.user_service.dataAccess.mapper;

import com.kawashreh.ecommerce.user_service.dataAccess.entity.UserEntity;
import com.kawashreh.ecommerce.user_service.domain.model.User;

import java.util.List;

public final class UserMapper {

    public static UserEntity toEntity(User d) {
        if (d == null) return null;

        return UserEntity
                .builder()
                .id(d.getId())
                .email(d.getEmail())
                .phone(d.getPhone())
                .birthdate(d.getBirthdate())
                .username(d.getUsername())
                .createdAt(d.getCreatedAt())
                .updatedAt(d.getUpdatedAt())
                .account(AccountMapper.toEntity(d.getAccount()))

                .addresses(
                        d.getAddresses() != null
                                ? d.getAddresses().stream()
                                .map(AddressMapper::toEntity)
                                .toList()
                                : List.of()
                )
                .build();

    }

    public static User toDomain(UserEntity e) {
        if (e == null) return null;
        return User
                .builder()
                .id(e.getId())
                .email(e.getEmail())
                .phone(e.getPhone())
                .birthdate(e.getBirthdate())
                .username(e.getUsername())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .account(AccountMapper.toDomain(e.getAccount()))
                .addresses(
                        e.getAddresses() != null
                                ? e.getAddresses().stream()
                                .map(AddressMapper::toDomain)
                                .toList()
                                : List.of()
                )

                .build();
    }
}
