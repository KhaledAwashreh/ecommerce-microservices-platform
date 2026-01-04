package com.kawashreh.ecommerce.user_service.dataAccess.Mapper;

import com.kawashreh.ecommerce.user_service.dataAccess.entity.UserEntity;
import com.kawashreh.ecommerce.user_service.domain.model.User;

public final class UserMapper extends DomainEntityMapper<UserEntity, User> {

    @Override
    public UserEntity toEntity(User d) {
        return UserEntity
                .builder()
                .id(d.getId())
                .email(d.getEmail())
                .phone(d.getPhone())
                .birthdate(d.getBirthdate())
                .username(d.getUsername())
                .createdAt(d.getCreatedAt())
                .updatedAt(d.getUpdatedAt())
                .build();

    }

    @Override
    public User toDomain(UserEntity e) {
        return User
                .builder()
                .id(e.getId())
                .email(e.getEmail())
                .phone(e.getPhone())
                .birthdate(e.getBirthdate())
                .username(e.getUsername())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }
}
