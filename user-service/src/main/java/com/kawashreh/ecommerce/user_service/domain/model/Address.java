package com.kawashreh.ecommerce.user_service.domain.model;

import lombok.*;
import lombok.experimental.Accessors;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class Address {

    private UUID id;

    private User user;

    private String street;

    private String city;

    private String state;

    private String postalCode;

    private String country;

    private boolean defaultAddress;

    private String phoneNumber;

    private String additionalInformation;

}
