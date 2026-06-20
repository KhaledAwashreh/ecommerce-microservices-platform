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
                .name(d.getName())
                .gender(d.getGender())
                .role(d.getRole() != null ? RoleMapper.toEntity(d.getRole()) : null)
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
                .name(e.getName())
                .gender(e.getGender())
                .account(e.getAccount() != null ? AccountMapper.toDomain(e.getAccount()) : null)
                .role(e.getRole() != null ? RoleMapper.toDomain(e.getRole()) : null)
                .build();
    }
}
