package com.kawashreh.ecommerce.user_service;

import com.kawashreh.ecommerce.common.exceptions.NoSuchElementException;
import com.kawashreh.ecommerce.user_service.constants.CacheConstants;
import com.kawashreh.ecommerce.user_service.dataAccess.repository.UserRepository;
import com.kawashreh.ecommerce.user_service.domain.enums.Gender;
import com.kawashreh.ecommerce.user_service.domain.service.UserService;
import com.kawashreh.ecommerce.user_service.domain.service.dto.UserCreateRequest;
import com.kawashreh.ecommerce.user_service.domain.service.dto.UserResponse;
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

    @Test
    void delete_shouldThrowAndNotDelete_whenRequestingUserIsNotOwner() {
        UserResponse created = createUser();
        UUID otherUserId = UUID.randomUUID();

        assertThatThrownBy(() -> userService.delete(created.getId(), otherUserId))
                .isInstanceOf(NoSuchElementException.class);

        assertThat(userRepository.findById(created.getId())).isPresent();
    }

    @Test
    void delete_shouldReturnSilently_whenUserDoesNotExist() {
        UUID nonExistentId = UUID.randomUUID();

        userService.delete(nonExistentId, nonExistentId);

        assertThat(userRepository.findById(nonExistentId)).isEmpty();
    }
}
