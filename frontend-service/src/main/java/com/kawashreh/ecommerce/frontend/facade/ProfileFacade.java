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
            // user stays null
        }

        try {
            addresses = addressServiceClient.getAddresses();
        } catch (Exception e) {
            // addresses stays empty
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
}