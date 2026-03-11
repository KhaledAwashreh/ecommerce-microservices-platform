package com.kawashreh.ecommerce.frontend.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddressDto {
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
