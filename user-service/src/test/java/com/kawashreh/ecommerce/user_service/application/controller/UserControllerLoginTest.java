package com.kawashreh.ecommerce.user_service.application.controller;

import com.kawashreh.ecommerce.common.exceptions.UnauthorizedException;
import com.kawashreh.ecommerce.user_service.application.dto.UserLoginDto;
import com.kawashreh.ecommerce.user_service.domain.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

// Regression test for GH #37: a failed login threw common.exceptions.NoSuchElementException,
// which GlobalExceptionHandler mapped to 404 - not defensible for a login failure (should be
// 401), and indistinguishable from the service's real "not found" cases.
@ExtendWith(MockitoExtension.class)
class UserControllerLoginTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private UserController controller;

    @Test
    void login_throwsUnauthorized_whenCredentialsAreInvalid() {
        when(userService.login(any(), any())).thenReturn(null);

        UserLoginDto dto = new UserLoginDto("someone", "wrong-password");

        assertThatThrownBy(() -> controller.login(dto))
                .isInstanceOf(UnauthorizedException.class);
    }
}
