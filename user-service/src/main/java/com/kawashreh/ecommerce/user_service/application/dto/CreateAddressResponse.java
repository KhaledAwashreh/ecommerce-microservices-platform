package com.kawashreh.ecommerce.user_service.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateAddressResponse {

    private UUID id;

    private String street;

    private String city;

    private String state;

    private String postalCode;

    private String country;

    private boolean defaultAddress;

    private String phoneNumber;

    private String additionalInformation;
}
