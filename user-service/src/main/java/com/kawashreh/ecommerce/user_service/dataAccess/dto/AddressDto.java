package com.kawashreh.ecommerce.user_service.dataAccess.dto;

import com.kawashreh.ecommerce.user_service.domain.model.User;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddressDto {

    @Id
    @GeneratedValue
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn( nullable = false)
    private User user;

    @Column(nullable = false)
    private String street;

    @Column(nullable = false)
    private String city;

    @Column(nullable = false)
    private String state;

    @Column(name = "postal_code", nullable = false)
    private String postalCode;

    @Column(nullable = false)
    private String country;

    @Builder.Default
    @Column(name = "is_default", nullable = false)
    private boolean defaultAddress = false;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column
    private String additionalInformation;

}
