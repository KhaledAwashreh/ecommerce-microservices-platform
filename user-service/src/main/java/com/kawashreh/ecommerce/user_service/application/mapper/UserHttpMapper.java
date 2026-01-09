package com.kawashreh.ecommerce.user_service.application.mapper;

import com.kawashreh.ecommerce.user_service.application.dto.UserDto;
import com.kawashreh.ecommerce.user_service.domain.model.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public final class UserHttpMapper {

    private UserHttpMapper() {} // Prevent instantiation

    // Domain -> DTO
    public static UserDto toDto(User user) {
        if (user == null) return null;

        return UserDto.builder()
                .id(user.getId())
                .name(user.getName())
                .username(user.getUsername())
                .email(user.getEmail())
                .birthdate(user.getBirthdate())
                .phone(user.getPhone())
                .gender(user.getGender())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    // DTO -> Domain
    public static User toDomain(UserDto dto) {
        if (dto == null) return null;

        return User.builder()
                .id(dto.getId())
                .name(dto.getName())
                .username(dto.getUsername())
                .email(dto.getEmail())
                .birthdate(dto.getBirthdate())
                .phone(dto.getPhone())
                .gender(dto.getGender())
                .createdAt(dto.getCreatedAt())
                .updatedAt(dto.getUpdatedAt())
                .build();
    }
}
