package com.kawashreh.ecommerce.user_service;

import com.kawashreh.ecommerce.common.exceptions.ForbiddenException;
import com.kawashreh.ecommerce.user_service.constants.CacheConstants;
import com.kawashreh.ecommerce.user_service.dataAccess.entity.AccountEntity;
import com.kawashreh.ecommerce.user_service.dataAccess.repository.AccountRepository;
import com.kawashreh.ecommerce.user_service.dataAccess.repository.UserRepository;
import com.kawashreh.ecommerce.user_service.domain.enums.Gender;
import com.kawashreh.ecommerce.user_service.domain.service.UserService;
import com.kawashreh.ecommerce.user_service.domain.service.dto.UserCreateRequest;
import com.kawashreh.ecommerce.user_service.domain.service.dto.UserResponse;
import com.kawashreh.ecommerce.user_service.domain.service.dto.UserUpdateRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ActiveProfiles("test")
class UserServiceImplIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private CacheManager cacheManager;

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

    @Test
    void delete_shouldRemoveUserAndEvictCaches() {
        UserResponse created = createUser();

        // Populate both caches that delete() is supposed to evict.
        userService.find(created.getId());
        userService.findByUsername(created.getUsername());

        Cache usersByIdCache = cacheManager.getCache(CacheConstants.USERS_BY_ID);
        Cache userByUsernameCache = cacheManager.getCache(CacheConstants.USER_BY_USERNAME);

        assertThat(usersByIdCache.get(created.getId())).isNotNull();
        assertThat(userByUsernameCache.get(created.getUsername())).isNotNull();

        userService.delete(created.getId(), created.getId());

        assertThat(userRepository.findById(created.getId())).isEmpty();
        assertThat(usersByIdCache.get(created.getId())).isNull();
        assertThat(userByUsernameCache.get(created.getUsername())).isNull();
    }

    // Regression test for GH #37: ownership-check failures used to throw
    // common.exceptions.NoSuchElementException (mapped to 404), conflating "not found"
    // with "not yours" (a 403-shaped condition). Now throws ForbiddenException.
    @Test
    void update_shouldEvictCaches() {
        UserResponse created = createUser();

        // Populate both caches that update() is supposed to evict.
        userService.find(created.getId());
        userService.findByUsername(created.getUsername());

        Cache usersByIdCache = cacheManager.getCache(CacheConstants.USERS_BY_ID);
        Cache userByUsernameCache = cacheManager.getCache(CacheConstants.USER_BY_USERNAME);

        assertThat(usersByIdCache.get(created.getId())).isNotNull();
        assertThat(userByUsernameCache.get(created.getUsername())).isNotNull();

        UserUpdateRequest updateRequest = UserUpdateRequest.builder()
                .id(created.getId())
                .requestingUserId(created.getId())
                .name("Updated Name")
                .build();

        userService.update(created.getId(), updateRequest);

        assertThat(usersByIdCache.get(created.getId())).isNull();
        assertThat(userByUsernameCache.get(created.getUsername())).isNull();

        UserResponse refreshed = userService.find(created.getId());
        assertThat(refreshed.getName()).isEqualTo("Updated Name");
    }

    @Test
    void changePassword_shouldEvictCaches() {
        UserResponse created = createUser();

        // Populate both caches that changePassword() is supposed to evict.
        userService.find(created.getId());
        userService.findByUsername(created.getUsername());

        Cache usersByIdCache = cacheManager.getCache(CacheConstants.USERS_BY_ID);
        Cache userByUsernameCache = cacheManager.getCache(CacheConstants.USER_BY_USERNAME);

        assertThat(usersByIdCache.get(created.getId())).isNotNull();
        assertThat(userByUsernameCache.get(created.getUsername())).isNotNull();

        UserResponse result = userService.changePassword(created.getUsername(), "Password123!", "NewPassword456!");

        assertThat(result).isNotNull();
        assertThat(usersByIdCache.get(created.getId())).isNull();
        assertThat(userByUsernameCache.get(created.getUsername())).isNull();
    }

    @Test
    void delete_shouldThrowForbiddenAndNotDelete_whenRequestingUserIsNotOwner() {
        UserResponse created = createUser();
        UUID otherUserId = UUID.randomUUID();

        assertThatThrownBy(() -> userService.delete(created.getId(), otherUserId))
                .isInstanceOf(ForbiddenException.class);

        assertThat(userRepository.findById(created.getId())).isPresent();
    }

    @Test
    void update_shouldThrowForbidden_whenRequestingUserIsNotOwner() {
        UserResponse created = createUser();
        UUID otherUserId = UUID.randomUUID();

        UserUpdateRequest request = UserUpdateRequest.builder()
                .id(created.getId())
                .requestingUserId(otherUserId)
                .name("Someone Else")
                .build();

        assertThatThrownBy(() -> userService.update(created.getId(), request))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void delete_shouldReturnSilently_whenUserDoesNotExist() {
        UUID nonExistentId = UUID.randomUUID();

        userService.delete(nonExistentId, nonExistentId);

        assertThat(userRepository.findById(nonExistentId)).isEmpty();
    }

    // Regression test for GH #33 (paired with GH #32): login() never checked
    // Account.isArchived(), so a banned/archived account could still authenticate
    // as long as the password matched.
    @Test
    void login_shouldFail_whenAccountIsArchived() {
        UserResponse created = createUser();

        AccountEntity account = accountRepository.findByUserId(created.getId()).orElseThrow();
        account.setArchived(true);
        accountRepository.save(account);

        String token = userService.login(created.getUsername(), "Password123!");

        assertThat(token).isNull();
    }

    @Test
    void login_shouldSucceed_whenAccountIsNotArchived() {
        UserResponse created = createUser();

        String token = userService.login(created.getUsername(), "Password123!");

        assertThat(token).isNotNull();
    }
}
