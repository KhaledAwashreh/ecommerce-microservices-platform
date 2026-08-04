package com.kawashreh.ecommerce.user_service;

import com.kawashreh.ecommerce.common.exceptions.ForbiddenException;
import com.kawashreh.ecommerce.user_service.domain.enums.Gender;
import com.kawashreh.ecommerce.user_service.domain.service.AddressService;
import com.kawashreh.ecommerce.user_service.domain.service.UserService;
import com.kawashreh.ecommerce.user_service.domain.service.dto.AddressCreateRequest;
import com.kawashreh.ecommerce.user_service.domain.service.dto.AddressResponse;
import com.kawashreh.ecommerce.user_service.domain.service.dto.AddressUpdateRequest;
import com.kawashreh.ecommerce.user_service.domain.service.dto.UserCreateRequest;
import com.kawashreh.ecommerce.user_service.domain.service.dto.UserResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Regression test for GH #37: ownership-check failures used to throw
// common.exceptions.NoSuchElementException (mapped to 404), conflating "not found" with
// "not yours" (a 403-shaped condition). Now throws ForbiddenException.
@ActiveProfiles("test")
class AddressServiceImplIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private AddressService addressService;

    private UserResponse createUser() {
        String unique = UUID.randomUUID().toString();

        UserCreateRequest request = UserCreateRequest.builder()
                .name("Test User")
                .username("user-" + unique)
                .email("user-" + unique + "@example.com")
                .birthdate(new Date())
                .phone("555-0100")
                .gender(Gender.MALE)
                .rawPassword("Password123!")
                .build();

        return userService.create(request);
    }

    private AddressResponse createAddress(UUID userId) {
        AddressCreateRequest request = AddressCreateRequest.builder()
                .userId(userId)
                .street("1 Main St")
                .city("Anytown")
                .state("CA")
                .postalCode("90210")
                .country("US")
                .defaultAddress(true)
                .phoneNumber("555-0100")
                .build();

        return addressService.create(request);
    }

    @Test
    void delete_shouldThrowForbidden_whenRequestingUserIsNotOwner() {
        UserResponse owner = createUser();
        AddressResponse address = createAddress(owner.getId());
        UUID otherUserId = UUID.randomUUID();

        assertThatThrownBy(() -> addressService.delete(address.getId(), otherUserId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void update_shouldThrowForbidden_whenRequestingUserIsNotOwner() {
        UserResponse owner = createUser();
        AddressResponse address = createAddress(owner.getId());
        UUID otherUserId = UUID.randomUUID();

        AddressUpdateRequest request = AddressUpdateRequest.builder()
                .id(address.getId())
                .requestingUserId(otherUserId)
                .street("Somewhere else")
                .build();

        assertThatThrownBy(() -> addressService.update(address.getId(), request))
                .isInstanceOf(ForbiddenException.class);
    }

    // Regression test for GH #64: getAll() had no scoping at all and returned every
    // address for every user. It must behave like search() and edit/delete - scoped to
    // the requesting user's own addresses only.
    @Test
    void getAll_shouldOnlyReturnCallingUsersOwnAddresses() {
        UserResponse userA = createUser();
        UserResponse userB = createUser();
        AddressResponse addressA = createAddress(userA.getId());
        AddressResponse addressB = createAddress(userB.getId());

        List<AddressResponse> resultForA = addressService.getAll(userA.getId());

        assertThat(resultForA)
                .extracting(AddressResponse::getId)
                .as("user A's getAll() must not include user B's address")
                .containsExactly(addressA.getId())
                .doesNotContain(addressB.getId());
    }
}
