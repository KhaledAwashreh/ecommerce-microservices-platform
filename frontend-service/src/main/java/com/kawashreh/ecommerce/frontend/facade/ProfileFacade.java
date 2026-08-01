package com.kawashreh.ecommerce.frontend.facade;

import com.kawashreh.ecommerce.frontend.client.AddressServiceClient;
import com.kawashreh.ecommerce.frontend.client.UserServiceClient;
import com.kawashreh.ecommerce.frontend.dto.AddressDto;
import com.kawashreh.ecommerce.frontend.dto.UserDto;
import com.kawashreh.ecommerce.frontend.dto.facade.ProfileWithAddressesDto;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Component
public class ProfileFacade {

    private final UserServiceClient userServiceClient;
    private final AddressServiceClient addressServiceClient;

    public ProfileFacade(UserServiceClient userServiceClient,
                         AddressServiceClient addressServiceClient) {
        this.userServiceClient = userServiceClient;
        this.addressServiceClient = addressServiceClient;
    }

    public ProfileWithAddressesDto getProfileWithAddresses(String username) {
        UserDto user = null;
        List<AddressDto> addresses = Collections.emptyList();

        try {
            user = userServiceClient.getUserByUsername(username);
        } catch (Exception e) {
            System.out.print(e.getMessage());
        }

        try {
            addresses = addressServiceClient.getAddresses();
        } catch (Exception e) {
            System.out.print(e.getMessage());
        }

        return ProfileWithAddressesDto.builder()
                .user(user)
                .addresses(addresses != null ? addresses : Collections.emptyList())
                .build();
    }

    public UserDto getUserByUsername(String username) {
        try {
            return userServiceClient.getUserByUsername(username);
        } catch (Exception e) {
            return null;
        }
    }

    public UserDto getUserById(UUID userId) {
        try {
            return userServiceClient.getUserById(userId);
        } catch (Exception e) {
            return null;
        }
    }

    public List<AddressDto> getAllAddresses() {
        try {
            return addressServiceClient.getAddresses();
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    // GH #58: addresses scoped to a single user, used by checkout (address selector +
    // ownership validation) - deliberately not getAllAddresses(), which returns every
    // address for every user. userId is unused here (GH #59: the server derives the
    // caller's identity from the session-authenticated X-User-ID header, not a
    // caller-supplied id) but kept in the signature since call sites pass the resolved
    // session user for readability.
    public List<AddressDto> getAddressesForUser(UUID userId) {
        try {
            List<AddressDto> addresses = addressServiceClient.searchAddresses();
            return addresses != null ? addresses : Collections.emptyList();
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}