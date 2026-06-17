package com.kawashreh.ecommerce.user_service.application.mapper;

import com.kawashreh.ecommerce.user_service.application.dto.CreateAddressRequest;
import com.kawashreh.ecommerce.user_service.application.dto.CreateAddressResponse;
import com.kawashreh.ecommerce.user_service.application.dto.AddressUpdateRequest;
import com.kawashreh.ecommerce.user_service.domain.service.dto.AddressCreateRequest;
import com.kawashreh.ecommerce.user_service.domain.service.dto.AddressResponse;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public final class AddressHttpMapper {

    private AddressHttpMapper() {}

    public static AddressCreateRequest toCreateRequest(CreateAddressRequest req, UUID userId) {
        if (req == null) return null;

        return AddressCreateRequest.builder()
                .userId(userId)
                .street(req.getStreet())
                .city(req.getCity())
                .state(req.getState())
                .postalCode(req.getPostalCode())
                .country(req.getCountry())
                .defaultAddress(req.isDefaultAddress())
                .phoneNumber(req.getPhoneNumber())
                .additionalInformation(req.getAdditionalInformation())
                .build();
    }

    public static com.kawashreh.ecommerce.user_service.domain.service.dto.AddressUpdateRequest toUpdateRequest(AddressUpdateRequest req, UUID requestingUserId) {
        if (req == null) return null;

        return com.kawashreh.ecommerce.user_service.domain.service.dto.AddressUpdateRequest.builder()
                .id(req.getId())
                .requestingUserId(requestingUserId)
                .street(req.getStreet())
                .city(req.getCity())
                .state(req.getState())
                .postalCode(req.getPostalCode())
                .country(req.getCountry())
                .defaultAddress(req.isDefaultAddress())
                .phoneNumber(req.getPhoneNumber())
                .additionalInformation(req.getAdditionalInformation())
                .build();
    }

    public static CreateAddressResponse toResponse(AddressResponse response) {
        if (response == null) return null;

        return CreateAddressResponse.builder()
                .id(response.getId())
                .street(response.getStreet())
                .city(response.getCity())
                .state(response.getState())
                .postalCode(response.getPostalCode())
                .country(response.getCountry())
                .defaultAddress(response.isDefaultAddress())
                .phoneNumber(response.getPhoneNumber())
                .additionalInformation(response.getAdditionalInformation())
                .build();
    }

    public static List<CreateAddressResponse> toResponseList(List<AddressResponse> responses) {
        if (responses == null) return null;

        return responses.stream()
                .map(AddressHttpMapper::toResponse)
                .collect(Collectors.toList());
    }
}
