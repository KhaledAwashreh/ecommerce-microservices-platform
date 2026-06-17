package com.kawashreh.ecommerce.user_service.application.mapper;

import com.kawashreh.ecommerce.user_service.application.dto.UserDto;
import com.kawashreh.ecommerce.user_service.application.dto.UserRegisterDto;
import com.kawashreh.ecommerce.user_service.application.dto.UserUpdateRequest;
import com.kawashreh.ecommerce.user_service.domain.service.dto.UserCreateRequest;
import com.kawashreh.ecommerce.user_service.domain.service.dto.UserResponse;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public final class UserHttpMapper {

    private UserHttpMapper() {}

    public static UserCreateRequest toCreateRequest(UserRegisterDto dto) {
        if (dto == null) return null;

        return UserCreateRequest.builder()
                .name(dto.getName())
                .username(dto.getUsername())
                .email(dto.getEmail())
                .birthdate(dto.getBirthdate())
                .phone(dto.getPhone())
                .gender(dto.getGender())
                .rawPassword(dto.getRawPassword())
                .build();
    }

    public static com.kawashreh.ecommerce.user_service.domain.service.dto.UserUpdateRequest toUpdateRequest(UserUpdateRequest dto, UUID requestingUserId) {
        if (dto == null) return null;

        return com.kawashreh.ecommerce.user_service.domain.service.dto.UserUpdateRequest.builder()
                .id(dto.getId())
                .requestingUserId(requestingUserId)
                .name(dto.getName())
                .email(dto.getEmail())
                .phone(dto.getPhone())
                .birthdate(dto.getBirthdate())
                .gender(dto.getGender())
                .build();
    }

    public static com.kawashreh.ecommerce.user_service.domain.service.dto.UserSearchRequest toSearchRequest(String query) {
        return com.kawashreh.ecommerce.user_service.domain.service.dto.UserSearchRequest.builder()
                .query(query)
                .build();
    }

    public static UserDto toDto(UserResponse response) {
        if (response == null) return null;

        return UserDto.builder()
                .id(response.getId())
                .name(response.getName())
                .username(response.getUsername())
                .email(response.getEmail())
                .birthdate(response.getBirthdate())
                .phone(response.getPhone())
                .gender(response.getGender())
                .build();
    }

    public static List<UserDto> toDtoList(List<UserResponse> responses) {
        if (responses == null) return null;

        return responses.stream()
                .map(UserHttpMapper::toDto)
                .collect(Collectors.toList());
    }
}
