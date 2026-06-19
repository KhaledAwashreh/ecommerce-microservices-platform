package com.kawashreh.ecommerce.user_service.application.mapper;

import com.kawashreh.ecommerce.user_service.application.dto.RoleRequest;
import com.kawashreh.ecommerce.user_service.application.dto.RoleResponse;
import com.kawashreh.ecommerce.user_service.domain.model.Role;

import java.util.List;
import java.util.stream.Collectors;

public final class RoleHttpMapper {

    private RoleHttpMapper() {}

    public static Role toDomain(RoleRequest req) {
        if (req == null) return null;

        return Role.builder()
                .name(req.getName())
                .description(req.getDescription())
                .build();
    }

    public static RoleResponse toResponse(Role role) {
        if (role == null) return null;

        return RoleResponse.builder()
                .id(role.getId())
                .name(role.getName())
                .description(role.getDescription())
                .permissions(role.getPermissions())
                .archived(role.isArchived())
                .archivedAt(role.getArchivedAt())
                .createdAt(role.getCreatedAt())
                .updatedAt(role.getUpdatedAt())
                .build();
    }

    public static List<RoleResponse> toResponseList(List<Role> roles) {
        if (roles == null) return null;

        return roles.stream()
                .map(RoleHttpMapper::toResponse)
                .collect(Collectors.toList());
    }
}
