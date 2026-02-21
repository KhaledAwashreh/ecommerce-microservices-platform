package com.kawashreh.ecommerce.user_service.domain.model;

import com.kawashreh.ecommerce.user_service.domain.enums.Gender;
import com.kawashreh.ecommerce.user_service.domain.enums.UserRole;
import com.kawashreh.ecommerce.user_service.infrastructure.security.PasswordHasher;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.*;

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

    @ToString.Exclude
    private Account account;

    @Builder.Default
    @ToString.Exclude
    private List<Address> addresses = new ArrayList<>();

    @Builder.Default
    @Enumerated(EnumType.STRING)
    private List<UserRole> roles = new ArrayList<>();

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
        return addresses == null ? Collections.emptyList() : Collections.unmodifiableList(addresses);
    }

    public void updateEmail(String newEmail) {
        this.email = newEmail;
        this.updatedAt = Instant.now();
        if (this.account != null) {
            this.account.setEmailVerified(false);
        }
    }

    public Address getDefaultAddress() {
        return addresses.stream()
                .filter(Address::isDefaultAddress)
                .findFirst()
                .orElse(null);
    }

    public void setDefaultAddress(UUID addressId) {
        // Clear current default
        addresses.forEach(a -> a.setDefaultAddress(false));

        // Set new default
        addresses.stream()
                .filter(a -> a.getId().equals(addressId))
                .findFirst()
                .ifPresent(a -> a.setDefaultAddress(true));
    }

    public void changePassword(String newRawPassword, PasswordHasher hasher) throws Exception {
        if (newRawPassword == null || newRawPassword.length() < 8) {
            throw new Exception("Password does not meet security requirements");
        }

        this.account.setHashedPassword(hasher.encode(newRawPassword));
        updatedAt = Instant.now();
    }

    public boolean checkPassword(String candidatePassword, PasswordHasher hasher) {
        return hasher.matches(candidatePassword, this.account.getHashedPassword());
    }

    public boolean isAdmin(){
        return roles.contains(UserRole.ADMIN);
    }

    public boolean isCustomer(){
        return roles.contains(UserRole.CUSTOMER);
    }

    public boolean isSeller(){
        return roles.contains(UserRole.SELLER);
    }
}
