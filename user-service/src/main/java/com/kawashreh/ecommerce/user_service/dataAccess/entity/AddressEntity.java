package com.kawashreh.ecommerce.user_service.dataAccess.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddressEntity {

    @Id
    @GeneratedValue
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id" ,nullable = false)
    private UserEntity user;

    @Column(name = "street", nullable = false)
    private String street;

    @Column(name = "city", nullable = false)
    private String city;

    @Column(name = "state", nullable = false)
    private String state;

    @Column(name = "postal_code", nullable = false)
    private String postalCode;

    @Column(name="country", nullable = false)
    private String country;

    @Builder.Default
    @Column(name = "is_default", nullable = false)
    private boolean defaultAddress = false;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "additional_information")
    private String additionalInformation;

}
