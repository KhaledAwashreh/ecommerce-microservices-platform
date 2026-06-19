package com.kawashreh.ecommerce.user_service.dataAccess.mapper;

import com.kawashreh.ecommerce.user_service.dataAccess.entity.RoleEntity;
import com.kawashreh.ecommerce.user_service.domain.model.Role;

public final class RoleMapper {

    public static RoleEntity toEntity(Role r) {
        if (r == null) return null;

        return RoleEntity.builder()
                .id(r.getId())
                .name(r.getName())
                .description(r.getDescription())
                .permissions(r.getPermissions())
                .archived(r.isArchived())
                .archivedAt(r.getArchivedAt())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }

    public static Role toDomain(RoleEntity e) {
        if (e == null) return null;

        return Role.builder()
                .id(e.getId())
                .name(e.getName())
                .description(e.getDescription())
                .permissions(e.getPermissions())
                .archived(e.isArchived())
                .archivedAt(e.getArchivedAt())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }
}
