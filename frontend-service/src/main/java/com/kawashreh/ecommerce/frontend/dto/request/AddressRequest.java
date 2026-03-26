package com.kawashreh.ecommerce.frontend.dto.request;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * Request DTO for address creation/update.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddressRequest {
    private String street;
    private String city;
    private String state;
    private String postalCode;
    private String country;
    private String phoneNumber;
    private String additionalInformation;
    private boolean defaultAddress;
}