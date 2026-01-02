package com.kawashreh.ecommerce.user_service.domain.model;

import com.kawashreh.ecommerce.user_service.domain.enums.Gender;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.*;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class User {

    private UUID id;

    private String name;

    private String username;

    private String email;

    private Date birthdate;

    private String phone;

    private Gender gender;

    private Instant createdAt;

    private Instant updatedAt;

    private Account account;

    private List<Address> addresses = new ArrayList<>();

    // Add business method
    public void addAddress(Address address) {
        if (address == null) {
            throw new IllegalArgumentException("Address cannot be null");
        }
        address.setUser(this);
        this.addresses.add(address);
        this.updatedAt = Instant.now();
    }

    public List<Address> getAddresses() {
        return Collections.unmodifiableList(addresses);
    }
}
